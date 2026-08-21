package com.metal.service;

import com.metal.entity.BaseMaterial156;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 156 项表新增/编辑回归测试：insert/update SQL 必须包含 accessory 列。
 * 背景：156 项表 part_name 改名"156项名称"+新增 accessory 列后，
 * 若数据库未执行迁移（缺 accessory 列），create/update 会报
 * "Unknown column 'accessory'" → 前端提示服务器异常（用户已实际复现）。
 * 本测试为修复后的回归保护：缺列时 create/update 抛异常，此处断言正常读写。
 */
@SpringBootTest
@Transactional
class BaseMaterial156CrudAccessoryTest {

    @Autowired
    private BaseMaterial156Service service;

    @Test
    void create_andUpdate_withAccessory_succeeds() {
        BaseMaterial156 r = new BaseMaterial156();
        r.setCompanyId(1L);
        r.setCategory("测试类别");
        r.setMaterialCode("ACC-TEST-" + System.currentTimeMillis());
        r.setSystemName("测试系统");
        r.setPartName("测试156项名称");
        r.setAccessory("测试配件");
        r.setUnitUsage(new BigDecimal("1.5"));
        r.setRatio(new BigDecimal("0.25"));
        r.setUnitPriceWithTax(new BigDecimal("120.5"));

        // 新增：insert SQL 带 accessory 列，缺列时报 Unknown column
        BaseMaterial156 created = service.create(r);
        assertNotNull(created.getId(), "新增应返回主键");
        assertNotNull(service.getById(created.getId()).getAccessory(), "新增后 accessory 应可读回");

        // 编辑：update SQL 带 accessory 列
        created.setPartName("测试156项名称-改");
        created.setAccessory("测试配件-改");
        service.update(created);
        BaseMaterial156 updated = service.getById(created.getId());
        assertEquals("测试156项名称-改", updated.getPartName());
        assertEquals("测试配件-改", updated.getAccessory());
    }
}
