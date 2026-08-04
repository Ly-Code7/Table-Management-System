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
        r.setPartName("TMP配件AUTO" + System.nanoTime()); // 唯一标记，避免与真实数据冲突
        r.setIsOutOfWarranty("未过保");
        r.setQuantity(quantity);
        return r;
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
        // 未过保 ← 是否过保（维修记录由 applyCalculations 自动计算；"无"→空）
        String expectedWarranty = "无".equals(saved.getIsOutOfWarranty()) ? "" : saved.getIsOutOfWarranty();
        assertEquals(expectedWarranty, uw.getWarrantyStatus());
        assertEquals(r.getPartName(), uw.getPartName());
        assertEquals(1, uw.getQuantity());
        assertEquals("TMP-MC-001", uw.getMaterialCode());
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
    void warrantyNone_mapsToEmpty() {
        OriginalRecord r = newRecord(2);
        r.setIsOutOfWarranty("无");
        OriginalRecord saved = originalRecordService.create(r);
        List<UnwarrantedMaterial> hits = unwarrantedMaterialMapper.search(1L, r.getPartName(), null, null, null, null, "id", "desc");
        assertEquals(1, hits.size());
        assertEquals("", hits.get(0).getWarrantyStatus());
    }
}
