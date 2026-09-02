package com.metal.service;

import com.metal.entity.OriginalRecord;
import com.metal.entity.UnwarrantedMaterial;
import com.metal.mapper.OriginalRecordMapper;
import com.metal.mapper.UnwarrantedMaterialMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 客户物料标记（2026-09）：
 * 维修记录"上机/下机是否客户物料"= 是 → 下推未过保物料的
 *   上机判定 mountJudgement = "客户物料"
 *   返修判定 warrantyStatus = "客户物料"（跳过按上次上机日期判定过保）
 * 集成测试连真实 MySQL，事务回滚不落库。
 */
@SpringBootTest
@Transactional
class UnwarrantedMaterialCustomerFlagTest {

    @Autowired
    private OriginalRecordService originalRecordService;

    @Autowired
    private OriginalRecordMapper originalRecordMapper;

    @Autowired
    private UnwarrantedMaterialMapper unwarrantedMaterialMapper;

    /** 构造维修记录：同 key（厂房-机台号+配件名）用于构造"上次维修"链条 */
    private OriginalRecord newRecord(String partName, LocalDate date, Integer quantity) {
        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(1L);
        r.setRecordDate(date);
        r.setFactory("测试厂房");
        r.setMachineNo("T-CUST-01");
        r.setFaultDescription("客户物料验证-处理方式");
        r.setMachineOnMaterial("TMP-CUST-ON");
        r.setRepairPerson("tester");
        r.setMaterialCode("TMP-CUST-MC");
        r.setMachineOffCode("TMP-CUST-OFF");
        r.setPartName(partName);
        r.setQuantity(quantity);
        return r;
    }

    private List<UnwarrantedMaterial> findByPartName(String partName) {
        return unwarrantedMaterialMapper.search(1L, partName, null, null, null, null, "id", "desc");
    }

    @Test
    void customerOnYes_pushMountJudgementCustomerMaterial() {
        // 上机是否客户物料 = 是 → 上机判定 = "客户物料"
        String part = "TMP配件CUST-ON-" + System.nanoTime();
        OriginalRecord r = newRecord(part, LocalDate.of(2026, 7, 1), 1);
        r.setMachineOnCustomer("是");
        OriginalRecord saved = originalRecordService.create(r);

        List<UnwarrantedMaterial> hits = findByPartName(part);
        assertEquals(1, hits.size());
        assertEquals("客户物料", hits.get(0).getMountJudgement());
        // 上机侧不影响返修判定：首修仍为空
        assertEquals("", hits.get(0).getWarrantyStatus());
    }

    @Test
    void customerOnNo_mountJudgementStaysNull() {
        // 上机是否客户物料 = 否 → 不下推上机判定（置空）
        String part = "TMP配件CUST-ONN-" + System.nanoTime();
        OriginalRecord r = newRecord(part, LocalDate.of(2026, 7, 1), 1);
        r.setMachineOnCustomer("否");
        originalRecordService.create(r);

        List<UnwarrantedMaterial> hits = findByPartName(part);
        assertEquals(1, hits.size());
        assertNull(hits.get(0).getMountJudgement());
    }

    @Test
    void customerOffYes_skipsWarrantyCalc_statusIsCustomerMaterial() {
        // 同 key 链条：A(6月) → B(7月, 下机是否客户物料=是)
        // B 距上次仅 1 个月（<6 应判"未过保"），但因下机客户物料 → 返修判定 = "客户物料"
        String part = "TMP配件CUST-OFF-" + System.nanoTime();
        OriginalRecord a = newRecord(part, LocalDate.of(2026, 6, 1), 1);
        originalRecordService.create(a);

        OriginalRecord b = newRecord(part, LocalDate.of(2026, 7, 1), 1);
        b.setMachineOffCustomer("是");
        originalRecordService.create(b);

        List<UnwarrantedMaterial> hits = findByPartName(part);
        assertEquals(2, hits.size());
        UnwarrantedMaterial uwB = hits.stream()
                .filter(u -> u.getRecordDate().equals(LocalDate.of(2026, 7, 1)))
                .findFirst().orElse(null);
        assertNotNull(uwB);
        assertEquals("客户物料", uwB.getWarrantyStatus(),
                "下机是否客户物料=是时应判'客户物料'，即使距上次<6个月也不判'未过保'");
        // 首条（无客户物料标志）不受影响
        UnwarrantedMaterial uwA = hits.stream()
                .filter(u -> u.getRecordDate().equals(LocalDate.of(2026, 6, 1)))
                .findFirst().orElse(null);
        assertNotNull(uwA);
        assertEquals("", uwA.getWarrantyStatus());
    }

    @Test
    void customerOffNo_normalWarrantyCalcStillApplies() {
        // 下机是否客户物料 = 否 → 正常按上次上机日期判：距上次 <6 个月 → "未过保"
        String part = "TMP配件CUST-OFFN-" + System.nanoTime();
        OriginalRecord a = newRecord(part, LocalDate.of(2026, 6, 1), 1);
        originalRecordService.create(a);

        OriginalRecord b = newRecord(part, LocalDate.of(2026, 7, 1), 1);
        b.setMachineOffCustomer("否");
        originalRecordService.create(b);

        List<UnwarrantedMaterial> hits = findByPartName(part);
        assertEquals(2, hits.size());
        UnwarrantedMaterial uwB = hits.stream()
                .filter(u -> u.getRecordDate().equals(LocalDate.of(2026, 7, 1)))
                .findFirst().orElse(null);
        assertNotNull(uwB);
        assertEquals("未过保", uwB.getWarrantyStatus(),
                "下机是否客户物料=否时按原逻辑：距上次<6个月 → 未过保");
        assertNull(uwB.getMountJudgement());
    }

    @Test
    void update_syncsCustomerFlagsBothDirections() {
        // 创建：上机=是 → 同步后 mountJudgement=客户物料；下机=否 → warrantyStatus 首修为空
        String part = "TMP配件CUST-UPD-" + System.nanoTime();
        OriginalRecord r = newRecord(part, LocalDate.of(2026, 7, 1), 1);
        r.setMachineOnCustomer("是");
        OriginalRecord saved = originalRecordService.create(r);

        List<UnwarrantedMaterial> hits = findByPartName(part);
        assertEquals(1, hits.size());
        assertEquals("客户物料", hits.get(0).getMountJudgement());

        // 编辑：下机改为"是" → 同步后返修判定 = 客户物料
        OriginalRecord edit = originalRecordService.getById(saved.getId());
        edit.setMachineOffCustomer("是");
        originalRecordService.update(edit);
        hits = findByPartName(part);
        assertEquals(1, hits.size());
        assertEquals("客户物料", hits.get(0).getWarrantyStatus());
        assertEquals("客户物料", hits.get(0).getMountJudgement());

        // 编辑：上机改回"否" → 上机判定清空；下机仍"是" → 返修判定保持客户物料
        edit = originalRecordService.getById(saved.getId());
        edit.setMachineOnCustomer("否");
        originalRecordService.update(edit);
        hits = findByPartName(part);
        assertNull(hits.get(0).getMountJudgement());
        assertEquals("客户物料", hits.get(0).getWarrantyStatus());

        // 编辑：下机也改回"否" → 返修判定还原为原逻辑（首修 → 空）
        edit = originalRecordService.getById(saved.getId());
        edit.setMachineOffCustomer("否");
        originalRecordService.update(edit);
        hits = findByPartName(part);
        assertEquals("", hits.get(0).getWarrantyStatus());
    }

    @Test
    void importExcel_customerFlagsPushed() throws Exception {
        // Excel 导入（含尾部两列）→ 下推记录带客户物料标记
        String part = "TMP配件CUST-IMP-" + System.nanoTime();
        OriginalRecord r = newRecord(part, LocalDate.of(2026, 7, 1), 1);
        r.setId(null);
        r.setMachineOnCustomer("是");
        r.setMachineOffCustomer("是");

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        com.alibaba.excel.EasyExcel.write(bos, OriginalRecord.class).sheet("维修记录").doWrite(List.of(r));
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());

        com.metal.dto.ImportResultDTO res = originalRecordService.importExcel(file, 1L);
        assertEquals(1, res.getSuccess());

        List<UnwarrantedMaterial> hits = findByPartName(part);
        assertEquals(1, hits.size());
        assertEquals("客户物料", hits.get(0).getMountJudgement());
        assertEquals("客户物料", hits.get(0).getWarrantyStatus());
    }

    @Test
    void customerOnYes_excludedFromMachineOnQuantityStats() {
        // 超比统计上机数量（2026-09 口径变更）："上机是否客户物料 = 是" 的记录不纳入统计。
        // 同料号三条：是(排除) + 否(计入) + 空/未设置(计入) → 月度与区间口径均只计 3+5=8
        OriginalRecord a = newRecord("TMP配件CUST-MOQ-A-" + System.nanoTime(), LocalDate.of(2026, 7, 1), 2);
        a.setMachineOnCustomer("是");
        originalRecordService.create(a);

        OriginalRecord b = newRecord("TMP配件CUST-MOQ-B-" + System.nanoTime(), LocalDate.of(2026, 7, 15), 3);
        b.setMachineOnCustomer("否");
        originalRecordService.create(b);

        OriginalRecord c = newRecord("TMP配件CUST-MOQ-C-" + System.nanoTime(), LocalDate.of(2026, 7, 20), 5);
        // machineOnCustomer 不设置（NULL）——历史数据/Excel 导入缺列场景
        originalRecordService.create(c);

        String mc = "TMP-CUST-MC";
        assertEquals(8, originalRecordMapper.countByMaterialCodeAndMonth(mc, "2026-07", 1L),
                "月度上机数量应排除'上机是否客户物料=是'的记录（2+3+5 → 8）");
        assertEquals(8, originalRecordMapper.countByMaterialCodeAndDateRange(mc, "2026-07-01", "2026-07-31", 1L),
                "区间上机数量应排除'上机是否客户物料=是'的记录（2+3+5 → 8）");
    }
}
