package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.metal.dto.ImportResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导入容错：Excel 数值列含错误值（如 #N/A）导致 BigDecimal 转换失败时，
 * 跳过该行继续导入，不中断整批（回归：2026-08-05 线上导入中断事故）。
 */
@SpringBootTest
@Transactional
class OriginalRecordImportFaultToleranceTest {

    @Autowired
    private OriginalRecordService originalRecordService;

    private static final List<String> NEW_HEAD = List.of(
            "年+月", "厂房+机台", "日期", "班次", "厂房", "序号", "机台号", "诊断人", "维修人",
            "报修时间", "开始时间", "结束时间", "维修工时", "停机工时", "机型", "故障现象",
            "维修描述", "物料编码", "156项名称", "零件名称", "数量", "上机物料", "下机物料",
            "备注", "确认人", "送货记录引用", "单据号");

    private MockMultipartFile buildFile(List<Object> goodRow, List<Object> badRow) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos).head(NEW_HEAD.stream().map(h -> List.of(h)).toList())
                .sheet("维修记录").doWrite(List.of(goodRow, badRow));
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }

    private List<Object> goodRow() {
        return List.of(
                "FY2607", "B5-H1", "2026-07-01", "白班", "B5", "001", "H1", "张", "李",
                "2026-07-01 08:00:00", "2026-07-01 08:30:00", "2026-07-01 09:00:00", 60, 30, "FANUC", "异响",
                "换件", "MC-001", "名称", "配件", 1, "ON", "OFF", "备注", "王", "2", "DOC1");
    }

    private List<Object> badRowWithHashError() {
        List<Object> r = new java.util.ArrayList<>(goodRow());
        r.set(12, "#N/A"); // 维修工时列 = Excel 错误值
        return r;
    }

    @Test
    void importWithHashErrorRow_skipsBadRowAndImportsGoodRow() throws Exception {
        MockMultipartFile file = buildFile(goodRow(), badRowWithHashError());

        ImportResultDTO res = originalRecordService.importExcel(file, 1L);

        // 不中断：正常行导入成功，坏行计入失败明细
        assertEquals(2, res.getTotal());
        assertEquals(1, res.getSuccess());
        assertEquals(1, res.getFail());
        assertEquals(1, res.getFailDetails().size());
        assertEquals(3, res.getFailDetails().get(0).getRow(), "失败行应为 Excel 第 3 行（表头1 + 数据行2）");
        assertTrue(res.getFailDetails().get(0).getReason().contains("BigDecimal"),
                "失败原因应指向 BigDecimal 转换失败，实际: " + res.getFailDetails().get(0).getReason());
    }

    private List<Object> rowWithUnstoppedText() {
        List<Object> r = new java.util.ArrayList<>(goodRow());
        r.set(12, "未停机"); // 维修工时列 = 业务文本（无停机，无工时）
        return r;
    }

    @Test
    void importRowWithUnstoppedText_importsSuccessfullyAsNull() throws Exception {
        MockMultipartFile file = buildFile(goodRow(), rowWithUnstoppedText());

        ImportResultDTO res = originalRecordService.importExcel(file, 1L);

        // 不中断且不失败：「未停机」映射为 null 工时，行正常导入
        assertEquals(2, res.getTotal());
        assertEquals(2, res.getSuccess());
        assertEquals(0, res.getFail());
        assertTrue(res.getFailDetails().isEmpty(),
                "「未停机」不应计入失败明细，实际: " + res.getFailDetails());
    }

    @Test
    void importAllGoodRows_noFail() throws Exception {
        MockMultipartFile file = buildFile(goodRow(), goodRow());

        ImportResultDTO res = originalRecordService.importExcel(file, 1L);

        assertEquals(2, res.getSuccess());
        assertEquals(0, res.getFail());
        assertTrue(res.getFailDetails().isEmpty());
    }
}
