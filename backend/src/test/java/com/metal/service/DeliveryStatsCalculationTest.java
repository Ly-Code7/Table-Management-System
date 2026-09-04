package com.metal.service;

import com.metal.common.PageResult;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.DeliveryRecord;
import com.metal.entity.DeliveryStats;
import com.metal.entity.OriginalRecord;
import com.metal.entity.SettlementMachine;
import com.metal.entity.UnwarrantedMaterial;
import com.metal.mapper.DeliveryRecordMapper;
import com.metal.mapper.DeliveryStatsMapper;
import com.metal.mapper.OriginalRecordMapper;
import com.metal.mapper.SettlementMachineMapper;
import com.metal.mapper.UnwarrantedMaterialMapper;
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

    @Autowired
    private SettlementMachineMapper settlementMachineMapper;

    @Autowired
    private UnwarrantedMaterialMapper unwarrantedMaterialMapper;

    @Autowired
    private UnwarrantedMaterialService unwarrantedMaterialService;

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
        r.setQuantity(quantity);
        originalRecordMapper.insert(r);
    }

    /** 插入未过保物料记录（超比统计"当月返修"的取数口径：未过保物料表，料号+月份+未过保） */
    private void addUnwarrantedMaterial(String materialCode, int quantity) {
        UnwarrantedMaterial u = new UnwarrantedMaterial();
        u.setCompanyId(1L);
        u.setRecordDate(LocalDate.now());
        u.setFactory("测试厂房");
        u.setMachineNo("T-CALC-01");
        u.setWarrantyStatus("未过保");
        u.setPartName("TMP配件UW" + System.nanoTime());
        u.setQuantity(quantity);
        u.setMaterialCode(materialCode);
        u.setCreatedBy("tester");
        u.setUpdatedBy("tester");
        unwarrantedMaterialMapper.batchInsert(List.of(u));
    }

    @Test
    void scheduler_refresh_findsStatsByYyyyMmAndAppliesAgreedRatioFormula() {
        String mc = "TMP-SCH-" + System.nanoTime();
        deliveryStatsMapper.insert(newStats(mc));
        addDelivery(mc, 30);
        addRepair(mc, 6);
        // 当月返修从未过保物料表取数（料号+月份+未过保）
        addUnwarrantedMaterial(mc, 6);
        // 再加一条"已过保"维修记录：只算上机数量，不算返修（未过保物料口径）
        OriginalRecord r2 = new OriginalRecord();
        r2.setCompanyId(1L);
        r2.setRecordDate(LocalDate.now());
        r2.setFactory("测试厂房");
        r2.setMachineNo("T-CALC-02");
        r2.setFaultDescription("计算测试-处理方式2");
        r2.setMachineOnMaterial("TMP-CALC-ON2");
        r2.setRepairPerson("tester");
        r2.setMaterialCode(mc);
        r2.setPartName("TMP配件CALC2" + System.nanoTime());
        r2.setQuantity(30);
        originalRecordMapper.insert(r2);

        scheduler.refreshCurrentMonthStats();

        List<DeliveryStats> hits = deliveryStatsMapper.findByYearMonth(month, 1L);
        DeliveryStats s = hits.stream().filter(x -> mc.equals(x.getMaterialCode())).findFirst().orElse(null);
        assertNotNull(s, "定时刷新应能按 yyyy-MM 格式查到当月统计记录（Bug1：FY 格式查不到）");
        assertEquals(30, s.getDeliveryQuantity());
        assertEquals(6, s.getMonthRepair());
        // 约定比例数量 = 单台机用量 2 × 比例 0.5 × 机台数 10 = 10.00
        assertEquals(0, new BigDecimal("10.00").compareTo(s.getAgreedRatioQuantity()), "约定比例数量应重算");
        // 超比数量 = max(0, 上机数量 36 - 返修 6 - 约定比例 10) = 20（Bug2：定时刷新不得漏减约定比例；公式用上机数量口径）
        assertEquals(0, new BigDecimal("20").compareTo(s.getExcessQuantity()), "超比数量应 = 上机-返修-约定比例");
        // 超比含税金额 = 100 × 20 ÷ 1.13 = 1769.91
        assertEquals(0, new BigDecimal("1769.91").compareTo(s.getExcessAmountWithTax()), "超比含税金额应重算");
    }

    @Test
    void importExcel_recalculatesDerivedFields() throws Exception {
        String mc = "TMP-IMP-" + System.nanoTime();
        DeliveryStats row = newStats(mc);
        row.setDeliveryQuantity(30);
        row.setMachineOnQuantity(30);
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
        // 超比数量 = max(0, 上机 30 - 返修 6 - 约定比例 10) = 14
        assertEquals(0, new BigDecimal("14").compareTo(s.getExcessQuantity()), "导入后超比数量应重算（上机-返修-约定比例）");
        // 超比含税金额 = 100 × 14 ÷ 1.13 = 1238.94
        assertEquals(0, new BigDecimal("1238.94").compareTo(s.getExcessAmountWithTax()), "导入后超比含税金额应重算");
    }

    @Test
    void importExcel_autoFillsMachineCountFromSettlementMachine() throws Exception {
        String mc = "TMP-MC-" + System.nanoTime();
        // 结算机台数：该料号当月结算 7 台（含多机型行，SUM 汇总）
        SettlementMachine sm1 = new SettlementMachine();
        sm1.setCompanyId(1L);
        sm1.setMaterialCode(mc);
        sm1.setCategory("测试类");
        sm1.setPartName("测试配件");
        sm1.setMachineModel("机型A");
        sm1.setSettlementMachineCount(5);
        sm1.setStatMonth(month);
        sm1.setCreatedBy("tester");
        sm1.setUpdatedBy("tester");
        SettlementMachine sm2 = new SettlementMachine();
        sm2.setCompanyId(1L);
        sm2.setMaterialCode(mc);
        sm2.setCategory("测试类");
        sm2.setPartName("测试配件");
        sm2.setMachineModel("机型B");
        sm2.setSettlementMachineCount(2);
        sm2.setStatMonth(month);
        sm2.setCreatedBy("tester");
        sm2.setUpdatedBy("tester");
        settlementMachineMapper.batchInsert(List.of(sm1, sm2));

        DeliveryStats row = newStats(mc);
        row.setDeliveryQuantity(30);
        row.setMonthRepair(6);
        row.setRatio(new BigDecimal("50"));
        // Excel 未提供机台数（留空）→ 应自动从结算机台数查询（5 + 2 = 7）
        row.setMachineCount(null);
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
        assertNotNull(s, "导入的记录应可按月份查到");
        assertEquals(7, s.getMachineCount(), "机台数应按料号从结算机台数 SUM 自动计算");
        // 约定比例数量随自动计算的机台数重算 = 2 × 0.5 × 7 = 7.00
        assertEquals(0, new BigDecimal("7.00").compareTo(s.getAgreedRatioQuantity()), "约定比例数量应按自动机台数重算");
    }

    @Test
    void importExcel_autoFillsFreeDeliveryQuantity() throws Exception {
        String mc = "TMP-FDQ-" + System.nanoTime();
        // 免费送货 8 条 + 非免费送货 5 条：免费数量应只统计 product_attr='免费'
        addDeliveryFree(mc, 8, "免费");
        addDeliveryFree(mc, 5, "新品");

        DeliveryStats row = newStats(mc);
        row.setMachineOnQuantity(30);
        row.setDeliveryQuantity(30);
        row.setMonthRepair(6);
        row.setRatio(new BigDecimal("50"));
        // Excel 未提供送货免费（留空）→ 应自动按料号+月份统计免费数量
        row.setFreeDeliveryQuantity(null);
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
        assertNotNull(s, "导入的记录应可按月份查到");
        assertEquals(8, s.getFreeDeliveryQuantity(), "送货免费应只统计产品属性为免费的送货数量");
    }

    @Test
    void importExcel_autoFillsMonthRepairFromUnwarrantedMaterial() throws Exception {
        String mc = "TMP-MRP-" + System.nanoTime();
        addUnwarrantedMaterial(mc, 7);

        DeliveryStats row = newStats(mc);
        row.setMachineOnQuantity(30);
        row.setDeliveryQuantity(30);
        row.setRatio(new BigDecimal("50"));
        // Excel 未提供当月返修（留空）→ 应自动按料号+月份从未过保物料统计
        row.setMonthRepair(null);
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
        assertNotNull(s, "导入的记录应可按月份查到");
        assertEquals(7, s.getMonthRepair(), "导入时当月返修应自动从未过保物料表统计");
        // 超比数量 = max(0, 上机30 - 返修7 - 约定比例10) = 13
        assertEquals(0, new BigDecimal("13").compareTo(s.getExcessQuantity()), "超比数量应随自动返修重算");
    }

    @Test
    void batchRefreshByMonth_appliesFreeDeliveryAndNewRepairSource() {
        String mc = "TMP-BRF-" + System.nanoTime();
        deliveryStatsMapper.insert(newStats(mc));
        addDeliveryFree(mc, 9, "免费");
        addDeliveryFree(mc, 3, "新品");
        addRepair(mc, 6);
        addUnwarrantedMaterial(mc, 6);

        int count = service.batchRefreshByMonth(month, month, 1L);
        assertEquals(1, count);

        List<DeliveryStats> hits = deliveryStatsMapper.findByYearMonth(month, 1L);
        DeliveryStats s = hits.stream().filter(x -> mc.equals(x.getMaterialCode())).findFirst().orElse(null);
        assertNotNull(s, "刷新后应能查到记录");
        assertEquals(12, s.getDeliveryQuantity(), "送货数量 = 免费+新品总和");
        assertEquals(9, s.getFreeDeliveryQuantity(), "手动刷新应同步计算送货免费");
        // 返修从未过保物料表取数（addUnwarrantedMaterial 6；addRepair 的维修记录不计入返修）
        assertEquals(6, s.getMonthRepair(), "手动刷新返修应从未过保物料表取数");
    }

    @Test
    void unwarrantedMaterial_warrantyStatus_calculatedFromLastRepair() {
        String part = "TMP配件WT" + System.nanoTime();
        String mc = "TMP-WT-" + System.nanoTime();

        // 首修（库中无上次维修记录）→ 未过保为空
        UnwarrantedMaterial first = new UnwarrantedMaterial();
        first.setCompanyId(1L);
        first.setRecordDate(LocalDate.of(2026, 1, 15));
        first.setFactory("测试厂房");
        first.setMachineNo("T-WT-01");
        first.setPartName(part);
        first.setMaterialCode(mc);
        first.setRepairPerson("tester");
        UnwarrantedMaterial saved1 = unwarrantedMaterialService.create(first);
        assertEquals("", saved1.getWarrantyStatus(), "首修（无上次维修）未过保应为空");

        // 间隔 2 个月（<6）→ 未过保
        UnwarrantedMaterial second = new UnwarrantedMaterial();
        second.setCompanyId(1L);
        second.setRecordDate(LocalDate.of(2026, 3, 15));
        second.setFactory("测试厂房");
        second.setMachineNo("T-WT-01");
        second.setPartName(part);
        second.setMaterialCode(mc);
        second.setRepairPerson("tester");
        UnwarrantedMaterial saved2 = unwarrantedMaterialService.create(second);
        assertEquals("未过保", saved2.getWarrantyStatus(), "距上次维修 <6 个月应判未过保");

        // 间隔 8 个月（>=6）→ 空
        UnwarrantedMaterial third = new UnwarrantedMaterial();
        third.setCompanyId(1L);
        third.setRecordDate(LocalDate.of(2026, 9, 15));
        third.setFactory("测试厂房");
        third.setMachineNo("T-WT-01");
        third.setPartName(part);
        third.setMaterialCode(mc);
        third.setRepairPerson("tester");
        UnwarrantedMaterial saved3 = unwarrantedMaterialService.create(third);
        assertEquals("", saved3.getWarrantyStatus(), "距上次维修 >=6 个月未过保应为空");
    }

    @Test
    void query_returnsLiveTotalCountByUniqueId() {
        String part = "TMP配件TC" + System.nanoTime();
        String mc = "TMP-TC-" + System.nanoTime();

        // 同唯一标识编号创建 3 条（不同日期，保持唯一编号一致）
        for (int i = 1; i <= 3; i++) {
            UnwarrantedMaterial u = new UnwarrantedMaterial();
            u.setCompanyId(1L);
            u.setRecordDate(LocalDate.of(2026, 1, i));
            u.setFactory("测试厂房");
            u.setMachineNo("T-TC-01");
            u.setPartName(part);
            u.setMaterialCode(mc);
            u.setRepairPerson("tester");
            unwarrantedMaterialService.create(u);
        }

        // 列表查询：3 条记录的总次数应全部为实时值 3（而非落库快照 1/2/3）
        PageResult<UnwarrantedMaterial> page = unwarrantedMaterialService.query(
                1, 100, 1L, part, null, null, null, null, "id", "desc");
        List<UnwarrantedMaterial> hits = page.getList().stream()
                .filter(x -> part.equals(x.getPartName())).toList();
        assertEquals(3, hits.size());
        for (UnwarrantedMaterial u : hits) {
            assertEquals(3, u.getTotalCount(), "同唯一标识编号的记录应显示实时总次数");
        }

        // 删除 1 条后：剩余 2 条的总次数应实时降为 2
        unwarrantedMaterialService.delete(hits.get(0).getId());
        PageResult<UnwarrantedMaterial> page2 = unwarrantedMaterialService.query(
                1, 100, 1L, part, null, null, null, null, "id", "desc");
        List<UnwarrantedMaterial> hits2 = page2.getList().stream()
                .filter(x -> part.equals(x.getPartName())).toList();
        assertEquals(2, hits2.size());
        for (UnwarrantedMaterial u : hits2) {
            assertEquals(2, u.getTotalCount(), "删除后总次数应实时减少");
        }
    }

    private void addDeliveryFree(String materialCode, int quantity, String productAttr) {
        DeliveryRecord r = new DeliveryRecord();
        r.setCompanyId(1L);
        r.setRecordDate(LocalDate.now());
        r.setCategory("测试类");
        r.setMaterialCode(materialCode);
        r.setQuantity(quantity);
        r.setProductAttr(productAttr);
        r.setYearMonth(month);
        r.setCreatedBy("tester");
        r.setUpdatedBy("tester");
        deliveryRecordMapper.insert(r);
    }

    @Test
    void effectiveAgreed_clipsQuotaToActualNetQuantity() {
        // 欠比例：额度 10，上机 8、返修 2 → 净 6 < 10 → 约定比例按实际净用量 6
        assertEquals(0, new BigDecimal("6").compareTo(
                DeliveryStatsService.effectiveAgreed(new BigDecimal("10"), 8, 2)));
        // 超比例：上机 20、返修 0 → 净 20 ≥ 额度 10 → 保持额度
        assertEquals(0, new BigDecimal("10").compareTo(
                DeliveryStatsService.effectiveAgreed(new BigDecimal("10"), 20, 0)));
        // 上机=0：净 0 → 约定比例 0（不依赖额度）
        assertEquals(0, BigDecimal.ZERO.compareTo(
                DeliveryStatsService.effectiveAgreed(new BigDecimal("10"), 0, 0)));
        // 上机 < 返修：净为负 → 取下界 0（不产生负约定比例）
        assertEquals(0, BigDecimal.ZERO.compareTo(
                DeliveryStatsService.effectiveAgreed(new BigDecimal("10"), 2, 5)));
        // 额度为空 → 按 0 计（与页面/导出原口径一致：null 额度行比例内金额按 0，净量全部由超比承担，恒等式仍成立）
        assertEquals(0, BigDecimal.ZERO.compareTo(
                DeliveryStatsService.effectiveAgreed(null, 8, 2)));
    }

    @Test
    void batchRefresh_keepsStoredAgreedAsQuota_unclipped() {
        String mc = "TMP-EFF-" + System.nanoTime();
        // 额度 10（机台 10 × 用量 2 × 比例 0.5）；刷新后上机 8、返修 2 → 欠比例（净 6 < 10）
        deliveryStatsMapper.insert(newStats(mc));
        addRepair(mc, 8);             // 上机数量数据源（original_record）
        addUnwarrantedMaterial(mc, 2); // 当月返修数据源（未过保物料）

        int count = service.batchRefreshByMonth(month, month, 1L);
        assertEquals(1, count);

        List<DeliveryStats> hits = deliveryStatsMapper.findByYearMonth(month, 1L);
        DeliveryStats s = hits.stream().filter(x -> mc.equals(x.getMaterialCode())).findFirst().orElse(null);
        assertNotNull(s, "刷新后应能查到记录");
        assertEquals(8, s.getMachineOnQuantity());
        assertEquals(2, s.getMonthRepair());
        // 存储约定比例数量仍为合同额度 10：口径裁剪只发生在展示/合计层，不落库
        assertEquals(0, new BigDecimal("10.00").compareTo(s.getAgreedRatioQuantity()),
                "存储约定比例数量应保持额度，裁剪不得落库");
        // 超比 = max(0, 8-2-10) = 0（欠比例不产生超比）
        assertEquals(0, BigDecimal.ZERO.compareTo(s.getExcessQuantity()));
        // 口径层裁剪：effAgreed = min(10, max(8-2, 0)) = 6 → 恒等式 上机8 = 返修2 + 约定比例6 + 超比0
        assertEquals(0, new BigDecimal("6").compareTo(
                DeliveryStatsService.effectiveAgreed(s.getAgreedRatioQuantity(), s.getMachineOnQuantity(), s.getMonthRepair())),
                "裁剪后的约定比例应等于实际净用量（上机−返修）");
    }

    @Test
    void exportExcel_exportsClippedAgreedRatioColumn() throws Exception {
        String mc = "TMP-EXP-" + System.nanoTime();
        // 额度 10（机台 10 × 用量 2 × 比例 0.5）；上机 2、返修 0 → 欠比例（净 2 < 10）
        deliveryStatsMapper.insert(newStats(mc));
        addRepair(mc, 2);
        int count = service.batchRefreshByMonth(month, month, 1L);
        assertEquals(1, count);

        // 调用导出（keyword 限定该料号；日期区间覆盖当月）
        java.time.YearMonth ym = java.time.YearMonth.parse(month);
        org.springframework.mock.web.MockHttpServletResponse resp =
                new org.springframework.mock.web.MockHttpServletResponse();
        service.exportExcel(resp, 1L, mc, null, null,
                ym.atDay(1).toString(), ym.atEndOfMonth().toString());

        byte[] bytes = resp.getContentAsByteArray();
        assertTrue(bytes.length > 0, "导出应有内容");
        // 无模型读回（Map<列号,值>）：导出尾部有 RowWriteHandler 追加的空行+合计行（标签文本列），
        // 有模型读会把合计行当数据行触发类型转换错误；无模型读无此问题
        java.util.List<java.util.Map<Integer, Object>> rows = com.alibaba.excel.EasyExcel.read(
                        new java.io.ByteArrayInputStream(bytes))
                .sheet().headRowNumber(1).doReadSync();
        java.util.Map<Integer, Object> hit = rows.stream()
                .filter(m -> m.values().stream().anyMatch(v -> v != null && mc.equals(String.valueOf(v))))
                .findFirst().orElse(null);
        assertNotNull(hit, "导出应包含该料号行");
        // 列 12 = 约定比例数量（与导出合计列 valueCols 对齐：约定比例数量12）：应 = effectiveAgreed(10, 2, 0) = 2
        Object agreed = hit.get(12);
        assertNotNull(agreed, "约定比例数量列不应为空");
        assertEquals(0, new BigDecimal(String.valueOf(agreed)).compareTo(new BigDecimal("2")),
                "导出行约定比例数量应按 effectiveAgreed 裁剪（页面行显示 2，导出行也应 2），实际=" + agreed);
    }
}
