package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.metal.entity.BaseMaterial156;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 156项表模板列序契约测试：
 * 类别、料号、系统名称、156项名称、配件、单台机用量、比例、含税单价
 * （part_name 显示名由"配件名称"改为"156项名称"，新增 accessory 列"配件"）
 */
class BaseMaterial156TemplateStructureTest {

    @Test
    void templateColumnOrder_partNameRenamedTo156NameAndAccessoryAdded() throws Exception {
        BaseMaterial156 template = new BaseMaterial156();
        template.setCategory("示例类别");
        template.setMaterialCode("示例料号");
        template.setSystemName("示例系统名称");
        template.setPartName("示例156项名称");
        template.setAccessory("示例配件");
        template.setUnitUsage(new BigDecimal("1.5"));
        template.setRatio(new BigDecimal("0.25"));
        template.setUnitPriceWithTax(new BigDecimal("120.5"));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, BaseMaterial156.class).sheet("156项").doWrite(List.of(template));

        List<java.util.Map<Integer, String>> rows =
                EasyExcel.read(new ByteArrayInputStream(bos.toByteArray()))
                        .headRowNumber(0)
                        .sheet().doReadSync();
        assertFalse(rows.isEmpty(), "应读到表头行");
        java.util.Map<Integer, String> head = rows.get(0);
        assertEquals("类别", head.get(0));
        assertEquals("料号", head.get(1));
        assertEquals("系统名称", head.get(2));
        assertEquals("156项名称", head.get(3));
        assertEquals("配件", head.get(4));
        assertEquals("单台机用量", head.get(5));
        assertEquals("比例", head.get(6));
        assertEquals("含税单价", head.get(7));
    }

    @Test
    void import_readsAccessoryColumnByIndex() throws Exception {
        BaseMaterial156 template = new BaseMaterial156();
        template.setCategory("备件");
        template.setMaterialCode("TEST-ACC");
        template.setSystemName("冷却系统");
        template.setPartName("主轴");
        template.setAccessory("丝杆");
        template.setUnitPriceWithTax(new BigDecimal("100"));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, BaseMaterial156.class).sheet("156项").doWrite(List.of(template));

        java.util.concurrent.atomic.AtomicReference<BaseMaterial156> readRef = new java.util.concurrent.atomic.AtomicReference<>();
        EasyExcel.read(new ByteArrayInputStream(bos.toByteArray()), BaseMaterial156.class,
                new com.alibaba.excel.event.AnalysisEventListener<BaseMaterial156>() {
                    @Override
                    public void invoke(BaseMaterial156 data, com.alibaba.excel.context.AnalysisContext ctx) {
                        readRef.set(data);
                    }

                    @Override
                    public void doAfterAllAnalysed(com.alibaba.excel.context.AnalysisContext ctx) {
                    }
                }).sheet().doRead();

        BaseMaterial156 data = readRef.get();
        assertNotNull(data);
        assertEquals("TEST-ACC", data.getMaterialCode());
        // 按 index 映射：列4(配件) 读入 accessory
        assertEquals("主轴", data.getPartName());
        assertEquals("丝杆", data.getAccessory());
        assertEquals(0, new BigDecimal("100").compareTo(data.getUnitPriceWithTax()));
    }
}
