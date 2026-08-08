package com.metal.service;

import com.metal.entity.DeliveryRecord;
import com.metal.mapper.DeliveryRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 送货记录模糊匹配接口集成测试（连真实 MySQL，事务回滚不落库）。
 * 覆盖：维修记录"下机料号"自动回填依赖的 lookup-fuzzy 行为——
 *   - 料号/序列号/物料名称 LIKE 命中最近一条
 *   - 部分关键字命中（模糊）
 *   - 无匹配返回 null
 */
@SpringBootTest
@Transactional
class DeliveryRecordFuzzyLookupTest {

    @Autowired
    private DeliveryRecordMapper deliveryRecordMapper;

    @Autowired
    private DeliveryRecordService service;

    private final String materialCode = "FUZZY-TEST-98765";
    private final String serial = "FUZZY-SERIAL-ABC123";

    private DeliveryRecord newRecord(Long companyId) {
        DeliveryRecord r = new DeliveryRecord();
        r.setCompanyId(companyId);
        r.setRecordDate(LocalDate.now());
        r.setCategory("测试类");
        r.setMaterialName("模糊测试物料XYZ");
        r.setSpecModel("SPEC-1");
        r.setMaterialCode(materialCode);
        r.setMaterialSerial(serial);
        r.setQuantity(5);
        r.setUnit("件");
        r.setBrand("测试品牌");
        r.setProductAttr("正常");
        r.setFactory("A1");
        r.setShipmentNo("SHIP-001");
        r.setYearMonth(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        return r;
    }

    @Test
    void fuzzyMatchByMaterialCodeHit() {
        deliveryRecordMapper.insert(newRecord(1L));
        DeliveryRecord hit = service.getFuzzyByKeyword("FUZZY-TEST", 1L);
        assertNotNull(hit, "料号部分关键字应模糊命中");
        assertEquals(materialCode, hit.getMaterialCode());
    }

    @Test
    void fuzzyMatchBySerialHit() {
        deliveryRecordMapper.insert(newRecord(1L));
        DeliveryRecord hit = service.getFuzzyByKeyword("SERIAL-ABC", 1L);
        assertNotNull(hit, "序列号部分关键字应模糊命中");
        assertEquals(serial, hit.getMaterialSerial());
    }

    @Test
    void fuzzyMatchByNameHit() {
        deliveryRecordMapper.insert(newRecord(1L));
        DeliveryRecord hit = service.getFuzzyByKeyword("模糊测试物料", 1L);
        assertNotNull(hit, "物料名称部分关键字应模糊命中");
        assertEquals(materialCode, hit.getMaterialCode());
    }

    @Test
    void fuzzyMatchNoHitReturnsNull() {
        deliveryRecordMapper.insert(newRecord(1L));
        DeliveryRecord hit = service.getFuzzyByKeyword("不存在的关键字ZZZ", 1L);
        assertNull(hit, "无匹配应返回 null（前端回填为空）");
    }

    @Test
    void fuzzyMatchBlankKeywordReturnsNull() {
        assertNull(service.getFuzzyByKeyword("  ", 1L), "空白关键字返回 null");
        assertNull(service.getFuzzyByKeyword(null, 1L), "null 关键字返回 null");
    }

    @Test
    void fuzzyMatchScopedToCompany() {
        deliveryRecordMapper.insert(newRecord(1L));
        DeliveryRecord hitOther = service.getFuzzyByKeyword("FUZZY-TEST", 99L);
        assertNull(hitOther, "其他公司不应命中（公司隔离）");
    }
}
