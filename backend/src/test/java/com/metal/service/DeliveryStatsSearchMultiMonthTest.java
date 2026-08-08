package com.metal.service;

import com.metal.common.PageResult;
import com.metal.entity.DeliveryStats;
import com.metal.mapper.DeliveryStatsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 送货超比统计「多选月份查询」集成测试（连真实 MySQL，事务回滚不落库）。
 * 覆盖：
 *  - Mapper.search 按月列表 IN 查询：多月返回多月行、单月只返回该月、空列表不拼条件
 *  - Service.query 逗号分隔月份串解析（"当月,上月" → total=2）、null 不抛错
 */
@SpringBootTest
@Transactional
class DeliveryStatsSearchMultiMonthTest {

    @Autowired
    private DeliveryStatsMapper deliveryStatsMapper;

    @Autowired
    private DeliveryStatsService service;

    private final String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    private final String prevMonth = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    private final String materialCode = "TEST-MULTI-MONTH-001";

    private DeliveryStats newStats(String yearMonth, LocalDate statDate) {
        DeliveryStats s = new DeliveryStats();
        s.setCompanyId(1L);
        s.setCategory("测试类");
        s.setMaterialCode(materialCode);
        s.setSystemName("测试系统");
        s.setPartName("测试配件");
        s.setUnitUsage(new java.math.BigDecimal("2"));
        s.setRatio(new java.math.BigDecimal("0.5"));
        s.setUnitPriceWithTax(new java.math.BigDecimal("100"));
        s.setMachineCount(10);
        s.setDeliveryQuantity(0);
        s.setMachineOnQuantity(0);
        s.setMonthRepair(0);
        s.setStatDate(statDate);
        s.setYearMonth(yearMonth);
        return s;
    }

    @Test
    void searchByMultiMonthsReturnsBothMonths() {
        deliveryStatsMapper.insert(newStats(currentMonth, LocalDate.now()));
        deliveryStatsMapper.insert(newStats(prevMonth, YearMonth.now().minusMonths(1).atDay(1)));

        // keyword 按唯一料号过滤，隔离库中既有数据（真实库已有上千条超比记录）
        List<DeliveryStats> list = deliveryStatsMapper.search(
                null, materialCode, null, List.of(currentMonth, prevMonth), "id", "asc");

        assertEquals(2, list.size(), "多月 IN 查询应返回该料号两个月的记录");
        for (DeliveryStats s : list) {
            assertTrue(List.of(currentMonth, prevMonth).contains(s.getYearMonth()),
                    "返回行的年月必须在所选月份集合内，实际: " + s.getYearMonth());
            assertEquals(materialCode, s.getMaterialCode());
        }
    }

    @Test
    void searchBySingleMonthReturnsOnlyThatMonth() {
        deliveryStatsMapper.insert(newStats(currentMonth, LocalDate.now()));
        deliveryStatsMapper.insert(newStats(prevMonth, YearMonth.now().minusMonths(1).atDay(1)));

        List<DeliveryStats> list = deliveryStatsMapper.search(
                null, materialCode, null, List.of(currentMonth), "id", "asc");

        assertEquals(1, list.size(), "单月查询只返回该月记录（IN 单值 ≡ 原等值语义）");
        assertEquals(currentMonth, list.get(0).getYearMonth());
    }

    @Test
    void searchWithEmptyMonthsDoesNotFilter() {
        deliveryStatsMapper.insert(newStats(currentMonth, LocalDate.now()));
        // 空列表 = 不拼月份条件，查全部（按料号过滤，只含刚插入的这条）
        List<DeliveryStats> list = deliveryStatsMapper.search(
                null, materialCode, null, java.util.List.of(), "id", "asc");
        assertNotNull(list, "空月份列表不应抛错，返回全量");
        assertEquals(1, list.size(), "空列表不拼月份条件，仍按料号过滤返回该料号全部月份");
    }

    @Test
    void serviceQueryParsesCommaSeparatedMonths() {
        deliveryStatsMapper.insert(newStats(currentMonth, LocalDate.now()));
        deliveryStatsMapper.insert(newStats(prevMonth, YearMonth.now().minusMonths(1).atDay(1)));

        PageResult<DeliveryStats> page = service.query(1, 20, 1L, materialCode, null,
                currentMonth + "," + prevMonth, "id", "asc");

        assertEquals(2, page.getTotal(), "逗号分隔月份串应被解析为多月 IN 查询");
    }

    @Test
    void serviceQueryWithNullMonthDoesNotThrow() {
        deliveryStatsMapper.insert(newStats(currentMonth, LocalDate.now()));

        PageResult<DeliveryStats> page = service.query(1, 20, 1L, materialCode, null,
                null, "id", "asc");

        assertNotNull(page, "null 月份不应抛错，查全部");
        assertEquals(1, page.getTotal(), "按料号过滤只返回刚插入的 1 条");
    }
}
