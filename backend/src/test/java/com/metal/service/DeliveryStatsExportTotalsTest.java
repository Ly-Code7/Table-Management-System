package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.metal.common.PageResult;
import com.metal.entity.DeliveryStats;
import com.metal.interceptor.JwtUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 DeliveryStatsService.exportExcel 底部合计行的真实输出：
 * 1. 合计标签/数值列位与数量列一一对应（送货金额合计@8 … 超比含税总额@14，0 基）；
 * 2. 新增"送货免费金额合计"@9，数值 = Σ(含税单价 × 送货免费) ÷ 1.13 ÷ 10000（与送货金额合计同口径）；
 * 3. 合计行在末行含 31 号明细（最后写入列 47）时仍生成（afterRowDispose 行号触发）。
 * 使用 2026-07：1656 行，其中 65 行 free_delivery_quantity > 0，可验证非零金额路径。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryStatsExportTotalsTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private DeliveryStatsService service;

    private static final String[] LABELS = {"送货金额合计", "送货免费金额合计", "上机金额合计", "返修金额合计",
            "比例内金额合计", "超比金额合计", "超比含税总额"};
    private static final int[] COLS = {8, 9, 10, 11, 12, 13, 14};
    private static final BigDecimal DIVISOR = new BigDecimal("11300"); // 1.13 × 10000，万元口径

    @Test
    void exportTotalsRowsAlignedAndFreeDeliveryAmountCalculated() throws Exception {
        String ym = "2026-07";
        MvcResult res = mockMvc.perform(get("/api/delivery-stats/export")
                        .param("yearMonth", ym)
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(1L, "tester", "测试", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        byte[] body = res.getResponse().getContentAsByteArray();
        assertTrue(body.length > 100, "导出内容不应为空");

        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(body));
        Sheet sheet = wb.getSheetAt(0);

        // 1) 表头列位：数量列应落在预期列
        Row head = sheet.getRow(0);
        String[] headNames = {"送货数量", "送货免费", "上机数量", "当月返修", "约定比例数量", "超比数量合计", "超比含税金额合计"};
        for (int i = 0; i < headNames.length; i++) {
            Cell c = head.getCell(COLS[i]);
            assertNotNull(c, "表头列 " + COLS[i] + " 不应为空");
            assertEquals(headNames[i], c.getStringCellValue(), "表头列 " + COLS[i] + " 应为 " + headNames[i]);
        }

        // 2) 数据行数（与导出同参同序：companyId/keyword/category 空，yearMonth，id asc）
        PageResult<DeliveryStats> pr = service.query(1, 0, null, null, null, ym, "id", "asc");
        List<DeliveryStats> rows = pr.getList();
        assertFalse(rows.isEmpty(), "2026-07 应有数据");
        int n = rows.size();

        // 3) 合计标签行（row n+3）
        Row labelRow = sheet.getRow(n + 3);
        assertNotNull(labelRow, "合计标签行应存在（末行含 31 号明细时也必须生成）");
        for (int i = 0; i < LABELS.length; i++) {
            Cell c = labelRow.getCell(COLS[i]);
            assertNotNull(c, "标签列 " + COLS[i] + " 不应为空");
            assertEquals(LABELS[i], c.getStringCellValue(), "标签列 " + COLS[i] + " 应为 " + LABELS[i]);
        }

        // 4) 值行（row n+4）：独立重算后与单元格值比对
        Row valueRow = sheet.getRow(n + 4);
        assertNotNull(valueRow, "合计值行应存在");
        BigDecimal[] sums = new BigDecimal[7];
        for (int i = 0; i < sums.length; i++) sums[i] = BigDecimal.ZERO;
        for (DeliveryStats s : rows) {
            BigDecimal price = s.getUnitPriceWithTax() != null ? s.getUnitPriceWithTax() : BigDecimal.ZERO;
            sums[0] = sums[0].add(price.multiply(BigDecimal.valueOf(s.getDeliveryQuantity() != null ? s.getDeliveryQuantity() : 0)));
            sums[1] = sums[1].add(price.multiply(BigDecimal.valueOf(s.getFreeDeliveryQuantity() != null ? s.getFreeDeliveryQuantity() : 0)));
            sums[2] = sums[2].add(price.multiply(BigDecimal.valueOf(s.getMachineOnQuantity() != null ? s.getMachineOnQuantity() : 0)));
            sums[3] = sums[3].add(price.multiply(BigDecimal.valueOf(s.getMonthRepair() != null ? s.getMonthRepair() : 0)));
            sums[4] = sums[4].add(price.multiply(s.getAgreedRatioQuantity() != null ? s.getAgreedRatioQuantity() : BigDecimal.ZERO));
            sums[5] = sums[5].add(price.multiply(s.getExcessQuantity() != null ? s.getExcessQuantity() : BigDecimal.ZERO));
            sums[6] = sums[6].add(s.getExcessAmountWithTax() != null ? s.getExcessAmountWithTax() : BigDecimal.ZERO);
        }
        for (int i = 0; i < 6; i++) {
            sums[i] = sums[i].divide(DIVISOR, 2, java.math.RoundingMode.HALF_UP);
        }
        sums[6] = sums[6].divide(new BigDecimal("10000"), 2, java.math.RoundingMode.HALF_UP);

        for (int i = 0; i < sums.length; i++) {
            Cell c = valueRow.getCell(COLS[i]);
            assertNotNull(c, "值列 " + COLS[i] + " 不应为空");
            double actual = c.getNumericCellValue();
            double expected = sums[i].doubleValue();
            assertEquals(expected, actual, 0.011, "列 " + COLS[i] + " 合计值应等于重算值 " + sums[i]);
        }

        // 5) 非零路径：2026-07 有免费送货数据，送货免费金额合计必须非零（验证公式非空转）
        assertTrue(sums[1].compareTo(BigDecimal.ZERO) > 0, "2026-07 送货免费金额合计应非零");
        wb.close();
    }
}
