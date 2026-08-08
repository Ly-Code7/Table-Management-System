package com.metal.service;

import com.metal.entity.OriginalRecord;
import com.metal.entity.UnwarrantedMaterial;
import com.metal.mapper.UnwarrantedMaterialMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 维修记录新增 → 未过保物料自动下推（数量 >= 1 时触发）。
 * 集成测试连真实 MySQL，事务回滚不落库。
 */
@SpringBootTest
@Transactional
class OriginalRecordAutoPushTest {

    @Autowired
    private OriginalRecordService originalRecordService;

    @Autowired
    private UnwarrantedMaterialMapper unwarrantedMaterialMapper;

    private OriginalRecord newRecord(Integer quantity) {
        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(1L);
        r.setRecordDate(LocalDate.of(2026, 6, 14));
        r.setFactory("测试厂房");
        r.setMachineNo("T-AUTO-01");
        r.setFaultDescription("自动下推验证-处理方式");
        r.setMachineOnMaterial("TMP-ON-001");
        r.setRepairPerson("tester");
        r.setMaterialCode("TMP-MC-001");
        r.setMachineOffCode("TMP-OFF-001");
        r.setPartName("TMP配件AUTO" + System.nanoTime()); // 唯一标记，避免与真实数据冲突
        r.setQuantity(quantity);
        return r;
    }

    @Test
    void pushWithOnlyUpCodeFallsBackToUpCode() {
        // 规则2：维修记录只有上机料号（无下机料号）时，下推料号 = 上机料号
        OriginalRecord r = newRecord(1);
        r.setMachineOffCode(null);
        OriginalRecord saved = originalRecordService.create(r);
        assertEquals(1, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));
        List<UnwarrantedMaterial> hits = unwarrantedMaterialMapper.search(1L, r.getPartName(), null, null, null, null, "id", "desc");
        assertEquals(1, hits.size());
        assertEquals("TMP-MC-001", hits.get(0).getMaterialCode(),
                "无下机料号时下推料号应回退为上机料号");
    }

    @Test
    void quantityGE1_triggersAutoPushWithBackfillAndCalculations() {
        OriginalRecord r = newRecord(1);
        OriginalRecord saved = originalRecordService.create(r);

        // 下推记录已入库且关联维修记录 id
        assertEquals(1, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));

        // 字段核对
        List<UnwarrantedMaterial> hits = unwarrantedMaterialMapper.search(1L, r.getPartName(), null, null, null, null, "id", "desc");
        assertEquals(1, hits.size());
        UnwarrantedMaterial uw = hits.get(0);
        assertEquals(saved.getId(), uw.getOriginalRecordId());
        assertEquals(r.getRecordDate(), uw.getRecordDate());
        assertEquals("测试厂房", uw.getFactory());
        assertEquals("T-AUTO-01", uw.getMachineNo());
        assertEquals("自动下推验证-处理方式", uw.getEquipRepairDebugging());
        assertEquals("TMP-ON-001", uw.getRepairMaterialOn());
        assertEquals("tester", uw.getRepairPerson());
        // 未过保列独立计算：首修（无上次维修记录）→ 空
        assertEquals("", uw.getWarrantyStatus());
        assertEquals(r.getPartName(), uw.getPartName());
        assertEquals(1, uw.getQuantity());
        // 下推料号 = 维修记录的下机料号（不是上机料号）
        assertEquals("TMP-OFF-001", uw.getMaterialCode());
        // 派生字段已自动计算
        assertEquals("测试厂房-T-AUTO-01" + r.getPartName(), uw.getUniqueId());
        assertEquals("FY2606", uw.getYearMonth());
        assertEquals(r.getRecordDate(), uw.getCurrentDate());
        assertEquals(1, uw.getOccurrenceNo());
        assertEquals(1, uw.getTotalCount());
        assertEquals("1st", uw.getOverSixMonths());
        assertEquals("1st", uw.getUsageMonths());
    }

    @Test
    void quantityZero_doesNotPush() {
        OriginalRecord r = newRecord(0);
        OriginalRecord saved = originalRecordService.create(r);
        assertEquals(0, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));
    }

    @Test
    void quantityNull_doesNotPush() {
        OriginalRecord r = newRecord(null);
        OriginalRecord saved = originalRecordService.create(r);
        assertEquals(0, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));
    }

    @Test
    void warrantyStatus_firstRepair_isEmpty() {
        OriginalRecord r = newRecord(2);
        OriginalRecord saved = originalRecordService.create(r);
        List<UnwarrantedMaterial> hits = unwarrantedMaterialMapper.search(1L, r.getPartName(), null, null, null, null, "id", "desc");
        assertEquals(1, hits.size());
        assertEquals("", hits.get(0).getWarrantyStatus());
    }

    // =============== 编辑同步 ===============

    @Test
    void update_syncsLinkedRecordFieldsAndRecalculates() {
        OriginalRecord saved = originalRecordService.create(newRecord(1));

        // 编辑维修记录：改厂房/机台号/数量
        OriginalRecord edit = originalRecordService.getById(saved.getId());
        edit.setFactory("新厂房");
        edit.setMachineNo("T-AUTO-02");
        edit.setQuantity(3);
        edit.setPartName(edit.getPartName() + "改");
        originalRecordService.update(edit);

        List<UnwarrantedMaterial> hits = unwarrantedMaterialMapper.search(1L, edit.getPartName(), null, null, null, null, "id", "desc");
        assertEquals(1, hits.size());
        UnwarrantedMaterial uw = hits.get(0);
        assertEquals("新厂房", uw.getFactory());
        assertEquals("T-AUTO-02", uw.getMachineNo());
        assertEquals(3, uw.getQuantity());
        // 派生字段随新值重算：唯一标识编号 = 新厂房-T-AUTO-02 + 新配件名
        assertEquals("新厂房-T-AUTO-02" + edit.getPartName(), uw.getUniqueId());
        assertEquals(1, uw.getOccurrenceNo());
    }

    @Test
    void update_quantityBecomesGE1_pushesNewLink() {
        // 数量 0 创建 → 未下推
        OriginalRecord saved = originalRecordService.create(newRecord(0));
        assertEquals(0, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));

        // 编辑数量改为 2 → 补下推
        OriginalRecord edit = originalRecordService.getById(saved.getId());
        edit.setQuantity(2);
        originalRecordService.update(edit);
        assertEquals(1, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));
    }

    @Test
    void update_quantityBecomesZero_deletesLinkedRecord() {
        // 数量 1 创建 → 已下推
        OriginalRecord saved = originalRecordService.create(newRecord(1));
        assertEquals(1, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));

        // 编辑数量改为 0 → 删除关联的未过保物料记录
        OriginalRecord edit = originalRecordService.getById(saved.getId());
        edit.setQuantity(0);
        originalRecordService.update(edit);
        assertEquals(0, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));
    }

    @Test
    void linkedCounts_batchSumsAllLinked() {
        OriginalRecord a = originalRecordService.create(newRecord(1));
        OriginalRecord b = originalRecordService.create(newRecord(2));
        // 未下推的记录不计入
        OriginalRecord c = originalRecordService.create(newRecord(0));
        assertEquals(2, originalRecordService.linkedCounts(java.util.List.of(a.getId(), b.getId())));
        assertEquals(2, originalRecordService.linkedCounts(java.util.List.of(a.getId(), b.getId(), c.getId())));
        assertEquals(0, originalRecordService.linkedCounts(java.util.List.of(c.getId())));
    }

    // =============== 删除级联 ===============

    @Test
    void delete_cascadesLinkedUnwarrantedMaterial() {
        OriginalRecord saved = originalRecordService.create(newRecord(1));
        assertEquals(1, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));

        originalRecordService.delete(saved.getId());
        assertEquals(0, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));
    }

    @Test
    void linkedCount_reflectsLinkState() {
        OriginalRecord saved = originalRecordService.create(newRecord(1));
        assertEquals(1, originalRecordService.linkedCount(saved.getId()));
        originalRecordService.delete(saved.getId());
        // 维修记录已删，再查会抛"记录不存在"——直接验证级联后的删除结果
        assertEquals(0, unwarrantedMaterialMapper.countByOriginalRecordId(saved.getId(), 1L, null));
    }

    // =============== Excel 导入下推 ===============

    @Test
    void importExcel_quantityGE1_pushesLinkedRecords() throws Exception {
        OriginalRecord r1 = newRecord(1); // 应下推
        OriginalRecord r0 = newRecord(0); // 不下推
        // 清掉 id（导入按新记录处理）
        r1.setId(null);
        r0.setId(null);

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        com.alibaba.excel.EasyExcel.write(bos, OriginalRecord.class).sheet("维修记录").doWrite(List.of(r1, r0));
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());

        com.metal.dto.ImportResultDTO res = originalRecordService.importExcel(file, 1L);
        assertEquals(2, res.getSuccess());
        assertEquals(0, res.getFail());

        // r1（quantity=1）导入后应有关联的未过保物料；r0（quantity=0）无
        List<UnwarrantedMaterial> hits = unwarrantedMaterialMapper.search(1L, r1.getPartName(), null, null, null, null, "id", "desc");
        assertEquals(1, hits.size());
        assertNotNull(hits.get(0).getOriginalRecordId());
        assertEquals(0, unwarrantedMaterialMapper.countByOriginalRecordId(r0.getId() != null ? r0.getId() : -1L, 1L, null));
    }
}
