package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.metal.entity.DeliveryRecord;
import com.metal.entity.OriginalRecord;
import com.metal.mapper.DeliveryRecordMapper;
import com.metal.mapper.OriginalRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 维修记录 Excel 导入"下机料号自动回填"集成测试（连真实 MySQL，事务回滚不落库）。
 * 覆盖：导入行有下机物料号且下机料号为空时——
 *   - 模糊匹配送货记录命中 → 下机料号回填为送货记录料号
 *   - 未命中 → 下机料号保持为空
 */
@SpringBootTest
@Transactional
class OriginalRecordImportMachineOffCodeTest {

    @Autowired
    private OriginalRecordService service;

    @Autowired
    private DeliveryRecordMapper deliveryRecordMapper;

    @Autowired
    private OriginalRecordMapper originalRecordMapper;

    private static final List<String> HEAD = List.of(
            "年+月", "厂房+机台", "日期", "班次", "厂房", "序号", "机台号", "诊断人", "维修人",
            "报修时间", "开始时间", "结束时间", "维修工时", "停机工时", "机型", "故障现象",
            "维修描述", "物料编码", "156项名称", "零件名称", "数量", "上机物料", "下机物料",
            "备注", "确认人", "送货记录引用", "单据号");

    private void seedDeliveryRecord(Long companyId) {
        DeliveryRecord r = new DeliveryRecord();
        r.setCompanyId(companyId);
        r.setRecordDate(LocalDate.now());
        r.setCategory("测试类");
        r.setMaterialName("下机回填测试物料");
        r.setSpecModel("SPEC");
        r.setMaterialCode("MC-OFF-001");
        r.setMaterialSerial("SER-OFF-001");
        r.setQuantity(2);
        r.setUnit("件");
        r.setBrand("品牌");
        r.setProductAttr("正常");
        r.setFactory("A1");
        r.setShipmentNo("SHIP-1");
        r.setYearMonth(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        deliveryRecordMapper.insert(r);
    }

    private MockMultipartFile buildFile(String machineOffMaterial) {
        return buildFile(machineOffMaterial, "2026-07-01 08:00:00", "2026-07-01 08:30:00", "2026-07-01 09:00:00");
    }

    private MockMultipartFile buildFile(String machineOffMaterial, String repairTime, String startTime, String endTime) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<List<Object>> data = new ArrayList<>();
        // 物料编码列留空（触发下机料号回填场景），下机物料列按入参，时间列按入参
        data.add(List.of(
                "FY2607", "B5-H1", "2026-07-01", "白班", "B5", "001", "H1", "张", "李",
                repairTime, startTime, endTime, 60, 30, "FANUC", "异响",
                "换件", "", "名称", "配件", 1, "ON", machineOffMaterial, "备注", "王", "2", "DOC1"));
        EasyExcel.write(bos).head(HEAD.stream().map(h -> List.of(h)).toList())
                .sheet("维修记录").doWrite(data);
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }

    private List<OriginalRecord> findImported(String keyword) {
        return originalRecordMapper.search(1L, keyword, null, null, null, null, "id", "desc", null);
    }

    @Test
    void importWithMachineOffMaterialFillsMachineOffCode() {
        seedDeliveryRecord(1L);
        var res = service.importExcel(buildFile("SER-OFF-00"), 1L); // 部分关键字，模糊命中序列号
        assertEquals(1, res.getSuccess());
        // search 的 keyword 可匹配 machine_off_material 列，用下机物料号定位导入行
        List<OriginalRecord> rows = findImported("SER-OFF-00");
        assertFalse(rows.isEmpty(), "导入后应存在下机料号回填的记录");
        // 断言：下机料号 = 送货记录的料号
        assertEquals("MC-OFF-001", rows.get(0).getMachineOffCode(),
                "下机物料号模糊命中送货记录时，下机料号应回填为送货记录料号");
        // 断言：物料编码（上机料号）列留空不应被回填（上机物料列 ON 未命中送货记录）
        assertTrue(rows.get(0).getMaterialCode() == null || rows.get(0).getMaterialCode().isBlank(),
                "上机物料 ON 未命中送货记录，料号保持为空");
    }

    @Test
    void importWithUnmatchedMachineOffMaterialKeepsEmpty() {
        seedDeliveryRecord(1L);
        var res = service.importExcel(buildFile("NO-SUCH-XYZ"), 1L);
        assertEquals(1, res.getSuccess());
        List<OriginalRecord> rows = findImported("NO-SUCH-XYZ");
        assertFalse(rows.isEmpty());
        assertTrue(rows.get(0).getMachineOffCode() == null || rows.get(0).getMachineOffCode().isBlank(),
                "下机物料号未命中送货记录时，下机料号保持为空");
    }

    @Test
    void importWithHmTimeTextFillsDatePartFromRecordDate() {
        // 历史数据：时间列是"只有时分"的文本（如 8:30），导入不应失败，日期部分用记录日期重建
        var res = service.importExcel(buildFile("OFF-X", "8:30", "8:30", "11:30"), 1L);
        assertEquals(1, res.getSuccess(), "只有时分的文本时间应能导入成功");
        List<OriginalRecord> rows = findImported("OFF-X");
        assertFalse(rows.isEmpty());
        assertEquals("2026-07-01T08:30", rows.get(0).getRepairRequestTime().toString(),
                "报修时间应重建为记录日期 + 时分");
        assertEquals("2026-07-01T11:30", rows.get(0).getEndTime().toString(),
                "结束时间应重建为记录日期 + 时分");
    }
}
