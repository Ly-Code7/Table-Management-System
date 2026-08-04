package com.metal.service;

import com.metal.dto.ImportResultDTO;
import com.metal.entity.DeliveryRecord;
import com.metal.entity.DeliveryStats;
import com.metal.entity.OriginalRecord;
import com.metal.mapper.DeliveryRecordMapper;
import com.metal.mapper.DeliveryStatsMapper;
import com.metal.mapper.OriginalRecordMapper;
import com.metal.scheduler.DeliveryStatsScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 送货超比统计计算口径集成测试（连真实 MySQL，事务回滚不落库）。
 * 覆盖：
 *  - 定时刷新按 yyyy-MM 格式查找当月统计（Bug1：原实现用 FY 格式查不到）
 *  - 定时刷新与页面/手动刷新同公式：超比数量 = max(0, 送货-返修-约定比例)（Bug2：原实现漏减约定比例）
 *  - Excel 导入重算派生字段（Bug3：原实现不重算，约定比例数量/超比数量/超比含税金额/年月为空）
 */
@SpringBootTest
@Transactional
class DeliveryStatsCalculationTest {

    @Autowired
    private DeliveryStatsScheduler scheduler;

    @Autowired
    private DeliveryStatsService service;

    @Autowired
    private DeliveryStatsMapper deliveryStatsMapper;

    @Autowired
    private DeliveryRecordMapper deliveryRecordMapper;

    @Autowired
    private OriginalRecordMapper originalRecordMapper;

    private final String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

    private DeliveryStats newStats(String materialCode) {
        DeliveryStats s = new DeliveryStats();
        s.setCompanyId(1L);
        s.setCategory("测试类");
        s.setMaterialCode(materialCode);
        s.setSystemName("测试系统");
        s.setPartName("测试配件");
        s.setUnitUsage(new BigDecimal("2"));
        s.setRatio(new BigDecimal("0.5"));
        s.setUnitPriceWithTax(new BigDecimal("100"));
        s.setMachineCount(10);
        s.setDeliveryQuantity(0);
        s.setMachineOnQuantity(0);
        s.setMonthRepair(0);
        s.setStatDate(LocalDate.now());
        s.setYearMonth(month);
        s.setCreatedBy("tester");
        s.setUpdatedBy("tester");
        return s;
    }

    private void addDelivery(String materialCode, int quantity) {
        DeliveryRecord r = new DeliveryRecord();
        r.setCompanyId(1L);
        r.setRecordDate(LocalDate.now());
        r.setCategory("测试类");
        r.setMaterialCode(materialCode);
        r.setQuantity(quantity);
        r.setYearMonth(month);
        r.setCreatedBy("tester");
        r.setUpdatedBy("tester");
        deliveryRecordMapper.insert(r);
    }

    private void addRepair(String materialCode, int quantity) {
        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(1L);
        r.setRecordDate(LocalDate.now());
        r.setFactory("测试厂房");
        r.setMachineNo("T-CALC-01");
        r.setFaultDescription("计算测试-处理方式");
        r.setMachineOnMaterial("TMP-CALC-ON");
        r.setRepairPerson("tester");
        r.setMaterialCode(materialCode);
        r.setPartName("TMP配件CALC" + System.nanoTime());
        r.setIsOutOfWarranty("未过保");
        r.setQuantity(quantity);
        originalRecordMapper.insert(r);
    }

    @Test
    void scheduler_refresh_findsStatsByYyyyMmAndAppliesAgreedRatioFormula() {
        String mc = "TMP-SCH-" + System.nanoTime();
        deliveryStatsMapper.insert(newStats(mc));
        addDelivery(mc, 30);
        addRepair(mc, 6);

        scheduler.refreshCurrentMonthStats();

        List<DeliveryStats> hits = deliveryStatsMapper.findByYearMonth(month, 1L);
        DeliveryStats s = hits.stream().filter(x -> mc.equals(x.getMaterialCode())).findFirst().orElse(null);
        assertNotNull(s, "定时刷新应能按 yyyy-MM 格式查到当月统计记录（Bug1：FY 格式查不到）");
        assertEquals(30, s.getDeliveryQuantity());
        assertEquals(6, s.getMonthRepair());
        // 约定比例数量 = 单台机用量 2 × 比例 0.5 × 机台数 10 = 10.00
        assertEquals(0, new BigDecimal("10.00").compareTo(s.getAgreedRatioQuantity()), "约定比例数量应重算");
        // 超比数量 = max(0, 送货 30 - 返修 6 - 约定比例 10) = 14（Bug2：定时刷新不得漏减约定比例）
        assertEquals(0, new BigDecimal("14").compareTo(s.getExcessQuantity()), "超比数量应 = 送货-返修-约定比例");
        // 超比含税金额 = 100 × 14 ÷ 1.13 = 1238.94
        assertEquals(0, new BigDecimal("1238.94").compareTo(s.getExcessAmountWithTax()), "超比含税金额应重算");
    }

    @Test
    void importExcel_recalculatesDerivedFields() throws Exception {
        String mc = "TMP-IMP-" + System.nanoTime();
        DeliveryStats row = newStats(mc);
        row.setDeliveryQuantity(30);
        row.setMonthRepair(6);
        // Excel 模板比例按百分比填写：50 表示 50%
        row.setRatio(new BigDecimal("50"));
        // 派生字段不在 Excel 中提供（留空），应由导入逻辑重算
        row.setAgreedRatioQuantity(null);
        row.setExcessQuantity(null);
        row.setExcessAmountWithTax(null);
        row.setYearMonth(null);

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        com.alibaba.excel.EasyExcel.write(bos, DeliveryStats.class).sheet("送货统计").doWrite(List.of(row));
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());

        ImportResultDTO res = service.importExcel(file, 1L);
        assertEquals(1, res.getSuccess());

        List<DeliveryStats> hits = deliveryStatsMapper.findByYearMonth(month, 1L);
        DeliveryStats s = hits.stream().filter(x -> mc.equals(x.getMaterialCode())).findFirst().orElse(null);
        assertNotNull(s, "导入的记录应可按月份查到（year_month 应由 statDate 生成，Bug3）");
        // 比例 50 → 0.5
        assertEquals(0, new BigDecimal("0.5").compareTo(s.getRatio()), "比例 50 应转为 0.5");
        // 约定比例数量 = 2 × 0.5 × 10 = 10.00
        assertEquals(0, new BigDecimal("10.00").compareTo(s.getAgreedRatioQuantity()), "导入后约定比例数量应重算");
        // 超比数量 = max(0, 30 - 6 - 10) = 14
        assertEquals(0, new BigDecimal("14").compareTo(s.getExcessQuantity()), "导入后超比数量应重算");
        // 超比含税金额 = 100 × 14 ÷ 1.13 = 1238.94
        assertEquals(0, new BigDecimal("1238.94").compareTo(s.getExcessAmountWithTax()), "导入后超比含税金额应重算");
    }
}
