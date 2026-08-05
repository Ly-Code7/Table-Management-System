package com.metal.scheduler;

import com.metal.entity.DeliveryStats;
import com.metal.entity.DeliveryStatsDaily;
import com.metal.entity.SysConfig;
import com.metal.mapper.*;
import com.metal.service.DeliveryStatsService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Component
public class DeliveryStatsScheduler {

    @Autowired
    private DeliveryStatsMapper deliveryStatsMapper;
    @Autowired
    private DeliveryStatsDailyMapper dailyMapper;
    @Autowired
    private DeliveryRecordMapper deliveryRecordMapper;
    @Autowired
    private OriginalRecordMapper originalRecordMapper;
    @Autowired
    private SysConfigMapper sysConfigMapper;

    /** 计算口径统一复用 Service（含约定比例数量/超比数量/超比含税金额公式），避免副本漂移 */
    @Autowired
    private DeliveryStatsService deliveryStatsService;

    private static final String DEFAULT_CRON = "0 0 3 * * *";
    private static final String CONFIG_KEY = "scheduler.cron";

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    private ScheduledFuture<?> scheduledTask;

    @PostConstruct
    public void init() {
        taskScheduler.initialize();
        taskScheduler.setPoolSize(1);
        scheduleTask();
    }

    /**
     * 读取数据库配置的 cron 表达式并调度任务
     */
    private void scheduleTask() {
        String cron = getCronFromDb();
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        scheduledTask = taskScheduler.schedule(this::refreshCurrentMonthStats, new CronTrigger(cron));
    }

    /**
     * 重新调度（配置变更后调用）
     */
    public void reschedule() {
        scheduleTask();
    }

    /**
     * 获取当前 cron 表达式
     */
    public String getCurrentCron() {
        return getCronFromDb();
    }

    /**
     * 更新 cron 表达式并重新调度
     */
    public void updateCron(String cron) {
        sysConfigMapper.updateValue(CONFIG_KEY, cron);
        reschedule();
    }

    private String getCronFromDb() {
        try {
            SysConfig config = sysConfigMapper.findByKey(CONFIG_KEY);
            if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
                return config.getConfigValue();
            }
        } catch (Exception ignored) {}
        return DEFAULT_CRON;
    }

    /**
     * 刷新当前月份的超比统计数据
     */
    public void refreshCurrentMonthStats() {
        LocalDate now = LocalDate.now();
        String currentMonth = now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // 注意：delivery_stats.year_month 存的是 yyyy-MM 格式（FY 格式是 unwarranted_material 表的约定）。
        // 曾用 FY 格式查询导致永远查不到记录、定时刷新空转。
        String yearMonth = currentMonth;

        List<DeliveryStats> statsList = deliveryStatsMapper.findByYearMonth(yearMonth, null);

        for (DeliveryStats stats : statsList) {
            String materialCode = stats.getMaterialCode();
            Long companyId = stats.getCompanyId();
            if (materialCode == null || materialCode.isBlank()) continue;

            int deliveryQty = deliveryRecordMapper.countByMaterialCodeAndMonth(materialCode, currentMonth, companyId);
            int freeDeliveryQty = deliveryRecordMapper.countFreeByMaterialCodeAndMonth(materialCode, currentMonth, companyId);
            int machineOnQty = originalRecordMapper.countByMaterialCodeAndMonth(materialCode, currentMonth, companyId);
            int repairQty = originalRecordMapper.countRepairByMaterialCodeAndMonth(materialCode, currentMonth, companyId);

            stats.setDeliveryQuantity(deliveryQty);
            stats.setFreeDeliveryQuantity(freeDeliveryQty);
            stats.setMachineOnQuantity(machineOnQty);
            stats.setMonthRepair(repairQty);

            deliveryStatsService.applyCalculations(stats);
            deliveryStatsMapper.update(stats);

            dailyMapper.deleteByStatId(stats.getId());
            List<Map<String, Object>> dailyCounts =
                    deliveryRecordMapper.countDailyByMaterialCodeAndMonth(materialCode, currentMonth, companyId);
            Map<Integer, Integer> dayMap = new HashMap<>();
            for (Map<String, Object> row : dailyCounts) {
                Number day = (Number) row.get("day");
                Number cnt = (Number) row.get("cnt");
                if (day != null && cnt != null) dayMap.put(day.intValue(), cnt.intValue());
            }
            int daysInMonth = YearMonth.parse(currentMonth).lengthOfMonth();
            List<DeliveryStatsDaily> dailies = new ArrayList<>();
            for (int d = 1; d <= daysInMonth; d++) {
                DeliveryStatsDaily daily = new DeliveryStatsDaily();
                daily.setStatId(stats.getId());
                daily.setDayNumber(d);
                daily.setValue(BigDecimal.valueOf(dayMap.getOrDefault(d, 0)));
                dailies.add(daily);
            }
            if (!dailies.isEmpty()) dailyMapper.batchInsert(dailies);
        }
    }
}
