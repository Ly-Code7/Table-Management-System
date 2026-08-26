package com.metal.service;

import com.metal.common.BizException;
import com.metal.entity.DeliveryStats;
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
 * 超比统计新增查重口径回归测试（连真实 MySQL，事务回滚不落库）。
 *
 * 背景 Bug（2026-08-26 现场）：前端表单在"编辑/复制后再新增"时，旧 yearMonth
 * 残留并随请求体上传；后端 create 的查重发生在 applyCalculations（按 statDate
 * 推导 yearMonth）之前，导致查重按请求体里的残留月份执行——
 *  - 残留月份存在同料号 → 误报"该月已存在料号 X 的统计记录"（用户录 8 月却查 6 月）；
 *  - 当月实际已有同料号 → 漏拦，插入重复记录。
 * 修复：create 先 applyCalculations 再查重，查重与入库统一按 statDate 口径。
 */
@SpringBootTest
@Transactional
class DeliveryStatsCreateDedupTest {

    @Autowired
    private DeliveryStatsService service;

    private final String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

    /** 构造一条统计记录：指定统计日期与请求体 yearMonth（模拟前端提交体） */
    private DeliveryStats stats(String materialCode, String statDate, String yearMonth) {
        DeliveryStats s = new DeliveryStats();
        s.setCompanyId(1L);
        s.setCategory("查重测试");
        s.setMaterialCode(materialCode);
        s.setSystemName("测试系统");
        s.setPartName("测试配件");
        s.setUnitUsage(new BigDecimal("1"));
        s.setRatio(new BigDecimal("0.1"));
        s.setUnitPriceWithTax(new BigDecimal("10"));
        s.setMachineCount(1);
        s.setDeliveryQuantity(0);
        s.setMachineOnQuantity(0);
        s.setMonthRepair(0);
        s.setStatDate(LocalDate.parse(statDate));
        s.setYearMonth(yearMonth);
        s.setCreatedBy("tester");
        s.setUpdatedBy("tester");
        return s;
    }

    /**
     * 用户场景：残留月份（2026-06）存在同料号记录，但 statDate 是当月（8 月），
     * 当月并无重复 —— 修复前误报"已存在"，修复后应正常创建。
     */
    @Test
    void 请求体残留旧月份_当月无重复时不应误报已存在() {
        String code = "DEDUP-" + System.nanoTime();
        // 残留月份 2026-06 存在同料号记录
        service.create(stats(code, "2026-06-01", "2026-06"), null);

        // 新增：statDate=当月，请求体残留 yearMonth=2026-06
        DeliveryStats record = stats(code, LocalDate.now().toString(), "2026-06");
        assertDoesNotThrow(() -> service.create(record, null),
                "按 statDate 口径当月无重复，不应报已存在");
    }

    /**
     * 反向场景：当月已存在同料号记录，请求体残留旧月份 —— 修复前漏拦插入重复，
     * 修复后应正确拦截。
     */
    @Test
    void 请求体残留旧月份_当月已有重复时应拦截() {
        String code = "DEDUP-" + System.nanoTime();
        // 当月已存在该料号记录
        service.create(stats(code, LocalDate.now().toString(), currentMonth), null);

        // 再新增一条当月记录，请求体残留旧月份 2026-06
        DeliveryStats record = stats(code, LocalDate.now().toString(), "2026-06");
        BizException ex = assertThrows(BizException.class, () -> service.create(record, null),
                "当月已有同料号记录，即使请求体残留旧月份也应拦截");
        assertTrue(ex.getMessage().contains("已存在"));
    }
}
