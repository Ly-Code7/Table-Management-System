package com.metal.service;

import com.metal.entity.DeliveryRecord;
import com.metal.entity.DeliveryStats;
import com.metal.entity.OriginalRecord;
import com.metal.entity.UnwarrantedMaterial;
import com.metal.mapper.DeliveryRecordMapper;
import com.metal.mapper.DeliveryStatsMapper;
import com.metal.mapper.OriginalRecordMapper;
import com.metal.mapper.UnwarrantedMaterialMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 超比统计「日期区间实时统计」集成测试（连真实 MySQL，事务回滚不落库）。
 *
 * 需求（方案 A 精确区间统计）：统计月份改为选择具体日期（如 2026-07-29 ~ 2026-08-31），
 * 送货数量/上机数量/返修按 record_date ∈ [start, end] 实时统计，跨月料号合并为一行；
 * 导出合并后的数据。
 *
 * 覆盖：
 *  - 跨月区间：同料号 7 月/8 月两条统计记录合并为一行，数量只统计区间内实际业务数据
 *  - 属性基座取最新月（id desc 第一条）
 *  - 机台数 = 区间覆盖各月结算机台数之和
 *  - 单月区间：每日明细按日分组填充
 *  - 非法区间（end < start / 空参）返回空
 */
@SpringBootTest
@Transactional
class DeliveryStatsRangeQueryTest {

    @Autowired
    private DeliveryStatsService service;

    @Autowired
    private DeliveryStatsMapper deliveryStatsMapper;
    @Autowired
    private DeliveryRecordMapper deliveryRecordMapper;
    @Autowired
    private OriginalRecordMapper originalRecordMapper;
    @Autowired
    private UnwarrantedMaterialMapper unwarrantedMaterialMapper;

    /** 唯一料号，隔离库中既有数据 */
    private final String materialCode = "RANGE-TEST-" + System.nanoTime();

    /** 造一条超比统计记录（指定年月/统计日期/单价，公司 1） */
    private DeliveryStats stats(String yearMonth, LocalDate statDate, BigDecimal unitPrice) {
        return statsFor(yearMonth, statDate, unitPrice, 1L);
    }

    /** 造一条超比统计记录（指定年月/统计日期/单价/公司） */
    private DeliveryStats statsFor(String yearMonth, LocalDate statDate, BigDecimal unitPrice, Long companyId) {
        DeliveryStats s = new DeliveryStats();
        s.setCompanyId(companyId);
        s.setCategory("区间测试类");
        s.setMaterialCode(materialCode);
        s.setSystemName("区间测试系统");
        s.setPartName("区间测试配件");
        s.setUnitUsage(new BigDecimal("2"));
        s.setRatio(new BigDecimal("0.5"));
        s.setUnitPriceWithTax(unitPrice);
        s.setMachineCount(0);
        s.setDeliveryQuantity(0);
        s.setMachineOnQuantity(0);
        s.setMonthRepair(0);
        s.setStatDate(statDate);
        s.setYearMonth(yearMonth);
        s.setCreatedBy("tester");
        s.setUpdatedBy("tester");
        return s;
    }

    /** 造一条送货记录（指定日期/数量/是否免费，公司 1） */
    private void delivery(LocalDate date, int qty, boolean free) {
        deliveryFor(date, qty, free, 1L);
    }

    /** 造一条送货记录（指定日期/数量/是否免费/公司） */
    private void deliveryFor(LocalDate date, int qty, boolean free, Long companyId) {
        DeliveryRecord r = new DeliveryRecord();
        r.setCompanyId(companyId);
        r.setRecordDate(date);
        r.setCategory("测试类");
        r.setMaterialName("区间测试物料");
        r.setMaterialCode(materialCode);
        r.setQuantity(qty);
        r.setProductAttr(free ? "免费" : "正常");
        r.setYearMonth(date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        r.setCreatedBy("tester");
        deliveryRecordMapper.insert(r);
    }

    /** 造一条上机（维修记录） */
    private void machineOn(LocalDate date, int qty) {
        OriginalRecord r = new OriginalRecord();
        r.setCompanyId(1L);
        r.setRecordDate(date);
        r.setMaterialCode(materialCode);
        r.setQuantity(qty);
        r.setYearMonth(date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        r.setCreatedBy("tester");
        originalRecordMapper.insert(r);
    }

    /** 造一条未过保返修物料 */
    private void repair(LocalDate date, int qty) {
        UnwarrantedMaterial u = new UnwarrantedMaterial();
        u.setCompanyId(1L);
        u.setRecordDate(date);
        u.setMaterialCode(materialCode);
        u.setQuantity(qty);
        u.setWarrantyStatus("未过保");
        u.setYearMonth(date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        u.setCreatedBy("tester");
        unwarrantedMaterialMapper.insert(u);
    }

    /** 造一条已入库的超比统计记录：机台数/约定比例数量按真实入库形态设置（服务端入库时 applyCalculations 算好落库） */
    private DeliveryStats insertStatsWith(String yearMonth, LocalDate statDate, int machineCount, String agreedRatio) {
        return insertStatsWith(yearMonth, statDate, machineCount, agreedRatio, new BigDecimal("100"));
    }

    private DeliveryStats insertStatsWith(String yearMonth, LocalDate statDate, int machineCount, String agreedRatio, BigDecimal unitPrice) {
        DeliveryStats s = stats(yearMonth, statDate, unitPrice);
        s.setMachineCount(machineCount);
        s.setAgreedRatioQuantity(new BigDecimal(agreedRatio));
        deliveryStatsMapper.insert(s);
        return s;
    }

    /**
     * 核心场景：7-29 ~ 8-31 跨月区间。
     * 7 月、8 月各有统计记录（单价不同，机台数 5/3、约定比例数量按入库口径 10.00/6.00），
     * 区间内业务数据：送货 7-30(5) + 8-15(3)，区间外：7-25(9)、9-01(7) 不应计入；
     * 免费送货 8-15(3) 计入"送货免费"；上机 8-10(4) 计入、9-01(6) 不计；
     * 返修 8-05(2) 计入、7-20(1) 不计。
     * 合并口径（用户确认）：机台数 = 7 月 + 8 月各自机台数之和（5 + 3 = 8）；
     * 约定比例数量 = 7 月 + 8 月各自的比例数量之和（10.00 + 6.00 = 16.00）。
     */
    @Test
    void 跨月区间_同料号合并为一行且数量只统计区间内() {
        insertStatsWith("2026-07", LocalDate.of(2026, 7, 15), 5, "10.00");
        insertStatsWith("2026-08", LocalDate.of(2026, 8, 20), 3, "6.00", new BigDecimal("120"));

        delivery(LocalDate.of(2026, 7, 25), 9, false);  // 区间外
        delivery(LocalDate.of(2026, 7, 30), 5, false);  // 区间内
        delivery(LocalDate.of(2026, 8, 15), 3, true);   // 区间内（免费）
        delivery(LocalDate.of(2026, 9, 1), 7, false);   // 区间外
        machineOn(LocalDate.of(2026, 8, 10), 4);        // 区间内
        machineOn(LocalDate.of(2026, 9, 1), 6);         // 区间外
        repair(LocalDate.of(2026, 7, 20), 1);           // 区间外
        repair(LocalDate.of(2026, 8, 5), 2);            // 区间内

        List<DeliveryStats> rows = service.queryRange(1L, materialCode, null, "2026-07-29", "2026-08-31", "desc");

        assertEquals(1, rows.size(), "跨月区间同料号应合并为一行");
        DeliveryStats row = rows.get(0);
        assertNull(row.getId(), "合并行不应有真实 id（区间视图只读）");
        assertEquals(materialCode, row.getMaterialCode());
        assertEquals("2026-07 ~ 2026-08", row.getYearMonth(), "合并行年月显示区间覆盖月份串");
        assertEquals(8, row.getDeliveryQuantity(), "送货数量 = 7-30(5) + 8-15(3)，区间外的 7-25/9-01 不计");
        assertEquals(3, row.getFreeDeliveryQuantity(), "免费送货 = 8-15(3)");
        assertEquals(4, row.getMachineOnQuantity(), "上机数量 = 8-10(4)，9-01 不计");
        assertEquals(2, row.getMonthRepair(), "返修 = 8-05(2)，7-20 不计");
        assertEquals(8, row.getMachineCount(), "机台数 = 7月(5) + 8月(3) 各自记录存值之和");
        assertEquals(0, new BigDecimal("120").compareTo(row.getUnitPriceWithTax()),
                "属性取最新月（8 月）记录的单价");
        // 约定比例数量 = 两月各自比例数量之和（10.00 + 6.00 = 16.00）；
        // 超比 = max(0, 上机4 - 返修2 - 约定比例16) = 0
        assertEquals(0, new BigDecimal("16.00").compareTo(row.getAgreedRatioQuantity()),
                "约定比例数量 = 7月(10.00) + 8月(6.00) 之和");
        assertEquals(0, row.getExcessQuantity().compareTo(BigDecimal.ZERO),
                "超比 = max(0, 上机4 - 返修2 - 约定比例16) = 0");
    }

    /** 单月区间：每日明细按日分组填充到 day01-31 */
    @Test
    void 单月区间_每日明细按日填充() {
        deliveryStatsMapper.insert(stats("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("100")));

        delivery(LocalDate.of(2026, 7, 29), 2, false);
        delivery(LocalDate.of(2026, 7, 30), 5, false);
        delivery(LocalDate.of(2026, 7, 31), 3, false);
        delivery(LocalDate.of(2026, 7, 28), 9, false);  // 区间外

        List<DeliveryStats> rows = service.queryRange(1L, materialCode, null, "2026-07-29", "2026-07-31", "desc");

        assertEquals(1, rows.size());
        DeliveryStats row = rows.get(0);
        assertEquals("2026-07", row.getYearMonth(), "单月区间年月显示该月");
        assertEquals(10, row.getDeliveryQuantity(), "送货 = 7-29(2)+7-30(5)+7-31(3)，7-28 不计");
        assertEquals(2, row.getDay29().intValue(), "29号明细 = 2");
        assertEquals(5, row.getDay30().intValue(), "30号明细 = 5");
        assertEquals(3, row.getDay31().intValue(), "31号明细 = 3");
        assertNull(row.getDay28(), "28号在区间外，明细不应有值");
    }

    /** 非法区间：end < start、空参数 → 空列表 */
    @Test
    void 非法区间返回空() {
        deliveryStatsMapper.insert(stats("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("100")));

        assertTrue(service.queryRange(1L, materialCode, null, "2026-08-31", "2026-07-29", "desc").isEmpty(),
                "end < start 应返回空");
        assertTrue(service.queryRange(1L, materialCode, null, null, "2026-08-31", "desc").isEmpty(),
                "startDate 为空应返回空");
        assertTrue(service.queryRange(1L, materialCode, null, "2026-07-29", "", "desc").isEmpty(),
                "endDate 为空应返回空");
    }

    /** 区间内无统计记录（该料号没建过统计）→ 空列表；其他料号不串入 */
    @Test
    void 区间内无统计记录返回空() {
        deliveryStatsMapper.insert(stats("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("100")));

        List<DeliveryStats> rows = service.queryRange(1L, materialCode, null, "2026-09-01", "2026-09-30", "desc");
        assertTrue(rows.isEmpty(), "区间覆盖月份内无该料号统计记录应返回空");
    }

    /** 跨月区间每日明细留空（31 列放不下 34 天） */
    @Test
    void 跨月区间每日明细留空() {
        deliveryStatsMapper.insert(stats("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("100")));
        deliveryStatsMapper.insert(stats("2026-08", LocalDate.of(2026, 8, 20), new BigDecimal("100")));
        delivery(LocalDate.of(2026, 7, 30), 5, false);

        DeliveryStats row = service.queryRange(1L, materialCode, null, "2026-07-29", "2026-08-31", "desc").get(0);
        assertNull(row.getDay30(), "跨月区间每日明细应留空（日号会叠加）");
        assertEquals(5, row.getDeliveryQuantity(), "合计列仍按区间统计");
    }

    /** 跨公司同料号：companyId 为空（查全部公司）时，A/B 两公司同料号应各自成行、数量按各自公司过滤 */
    @Test
    void 跨公司同料号_companyId为空时各自成行() {
        // 公司 1 与公司 2 同料号各一条统计记录 + 各一条送货记录
        deliveryStatsMapper.insert(statsFor("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("100"), 1L));
        deliveryStatsMapper.insert(statsFor("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("120"), 2L));
        deliveryFor(LocalDate.of(2026, 7, 20), 5, false, 1L);
        deliveryFor(LocalDate.of(2026, 7, 21), 7, false, 2L);

        List<DeliveryStats> rows = service.queryRange(null, materialCode, null, "2026-07-01", "2026-07-31", "desc");

        assertEquals(2, rows.size(), "两公司同料号在 companyId 为空时应各自成行，不得静默合并");
        DeliveryStats rowC1 = rows.stream().filter(r -> r.getCompanyId() != null && r.getCompanyId() == 1L).findFirst().orElse(null);
        DeliveryStats rowC2 = rows.stream().filter(r -> r.getCompanyId() != null && r.getCompanyId() == 2L).findFirst().orElse(null);
        assertNotNull(rowC1, "公司 1 的行不应丢失");
        assertNotNull(rowC2, "公司 2 的行不应丢失");
        assertEquals(5, rowC1.getDeliveryQuantity(), "公司 1 送货数量只统计公司 1 的记录");
        assertEquals(7, rowC2.getDeliveryQuantity(), "公司 2 送货数量只统计公司 2 的记录");
        assertEquals(0, new BigDecimal("100").compareTo(rowC1.getUnitPriceWithTax()), "公司 1 属性取公司 1 的记录");
        assertEquals(0, new BigDecimal("120").compareTo(rowC2.getUnitPriceWithTax()), "公司 2 属性取公司 2 的记录");
    }

    /** 导出排序：asc 与 desc 输出顺序相反（合并行按基础记录查询顺序输出） */
    @Test
    void 导出排序asc与desc相反() {
        String codeA = "RANGE-ORDER-A-" + System.nanoTime();
        String codeB = "RANGE-ORDER-B-" + System.nanoTime();
        // 两个料号各自一条记录，B 后插入（id 更大）
        DeliveryStats sa = stats("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("100"));
        sa.setMaterialCode(codeA);
        deliveryStatsMapper.insert(sa);
        DeliveryStats sb = stats("2026-07", LocalDate.of(2026, 7, 15), new BigDecimal("100"));
        sb.setMaterialCode(codeB);
        deliveryStatsMapper.insert(sb);

        List<DeliveryStats> desc = service.queryRange(1L, null, null, "2026-07-01", "2026-07-31", "desc");
        List<DeliveryStats> asc = service.queryRange(1L, null, null, "2026-07-01", "2026-07-31", "asc");

        // 过滤出本次测试的两个料号（区间内可能有库中其他料号）
        List<DeliveryStats> descFiltered = desc.stream().filter(r -> r.getMaterialCode().startsWith("RANGE-ORDER-")).toList();
        List<DeliveryStats> ascFiltered = asc.stream().filter(r -> r.getMaterialCode().startsWith("RANGE-ORDER-")).toList();
        assertEquals(2, descFiltered.size(), "desc 应含两个测试料号");
        assertEquals(2, ascFiltered.size(), "asc 应含两个测试料号");
        // B id 更大 → desc 在前；asc 相反
        assertEquals(codeB, descFiltered.get(0).getMaterialCode(), "desc 时最新（id 大）料号在前");
        assertEquals(codeA, ascFiltered.get(0).getMaterialCode(), "asc 时顺序与 desc 相反");
    }
}
