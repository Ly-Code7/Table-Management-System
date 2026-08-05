package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.metal.entity.OriginalRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模板列序契约测试：维修记录导入模板列序为
 * 年+月、厂房+机台、日期、班次、厂房、序号、机台号、…
 * （"厂房+机台"位于"年+月"与"日期"之间）
 */
class OriginalRecordTemplateStructureTest {

    @Test
    void templateColumnOrder_plantMachineBetweenYearMonthAndDate() throws Exception {
        // 构造模板示例数据（与 downloadTemplate 相同结构）
        OriginalRecord template = new OriginalRecord();
        template.setYearMonth("FY2607");
        template.setRecordDate(LocalDate.now());
        template.setShift("白班");
        template.setPlantMachine("示例厂房-示例机台号");
        template.setFactory("示例厂房");
        template.setSerialNumber("示例序号");
        template.setMachineNo("示例机台号");
        template.setDiagnostician("示例诊断人");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, OriginalRecord.class).sheet("维修记录").doWrite(List.of(template));

        // 读回全部行（含表头行，headRowNumber=0），无模型读取：每行 Map<列号, 值>
        List<java.util.Map<Integer, String>> rows =
                EasyExcel.read(new ByteArrayInputStream(bos.toByteArray()))
                        .headRowNumber(0)
                        .sheet().doReadSync();
        assertFalse(rows.isEmpty(), "应读到表头行");
        java.util.Map<Integer, String> head = rows.get(0);
        assertEquals("年+月", head.get(0));
        assertEquals("厂房+机台", head.get(1));
        assertEquals("日期", head.get(2));
        assertEquals("班次", head.get(3));
        assertEquals("厂房", head.get(4));
        assertEquals("序号", head.get(5));
        assertEquals("机台号", head.get(6));
        assertEquals("诊断人", head.get(7));
        // 物料区：物料编码 → 156项名称 → 零件名称
        assertEquals("物料编码", head.get(17));
        assertEquals("156项名称", head.get(18));
        assertEquals("零件名称", head.get(19));
        assertEquals("单据号", head.get(head.size() - 1));
    }

    @Test
    void import_readsPlantMachineColumnByIndex() throws Exception {
        OriginalRecord template = new OriginalRecord();
        template.setYearMonth("FY2607");
        template.setRecordDate(LocalDate.of(2026, 7, 1));
        template.setShift("白班");
        template.setPlantMachine("B5-H1");
        template.setFactory("B5");
        template.setMachineNo("H1");
        template.setDiagnostician("张三");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, OriginalRecord.class).sheet("维修记录").doWrite(List.of(template));

        AtomicReference<OriginalRecord> readRef = new AtomicReference<>();
        EasyExcel.read(new ByteArrayInputStream(bos.toByteArray()), OriginalRecord.class,
                new com.alibaba.excel.event.AnalysisEventListener<OriginalRecord>() {
                    @Override
                    public void invoke(OriginalRecord data, com.alibaba.excel.context.AnalysisContext ctx) {
                        readRef.set(data);
                    }

                    @Override
                    public void doAfterAllAnalysed(com.alibaba.excel.context.AnalysisContext ctx) {
                    }
                }).sheet().doRead();

        OriginalRecord data = readRef.get();
        assertNotNull(data);
        // 按 index 映射：列1(厂房+机台) 读入 plantMachine
        assertEquals("B5-H1", data.getPlantMachine());
        assertEquals(LocalDate.of(2026, 7, 1), data.getRecordDate());
        assertEquals("B5", data.getFactory());
        assertEquals("H1", data.getMachineNo());
        assertEquals("张三", data.getDiagnostician());
    }
}
