package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.*;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.metal.common.BizException;
import com.metal.common.PageResult;
import com.metal.common.ServiceHelper;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.DeliveryStats;
import com.metal.entity.DeliveryStatsDaily;
import com.metal.mapper.DeliveryStatsDailyMapper;
import com.metal.mapper.DeliveryStatsMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeliveryStatsService {

    @Autowired
    private DeliveryStatsMapper mapper;

    @Autowired
    private DeliveryStatsDailyMapper dailyMapper;

    @Autowired
    private com.metal.mapper.BaseMaterial156Mapper baseMaterial156Mapper;
    @Autowired
    private com.metal.mapper.DeliveryRecordMapper deliveryRecordMapper;
    @Autowired
    private com.metal.mapper.OriginalRecordMapper originalRecordMapper;
    @Autowired
    private com.metal.mapper.SettlementMachineMapper settlementMachineMapper;

    @Autowired
    private com.metal.mapper.UnwarrantedMaterialMapper unwarrantedMaterialMapper;

    @Autowired
    private OperationLogService logService;

    public PageResult<DeliveryStats> query(int page, int pageSize, Long companyId, String keyword,
                                            String category, String yearMonth,
                                            String sortField, String sortOrder) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<DeliveryStats> list = mapper.search(companyId, keyword, category, parseYearMonths(yearMonth), sortField, sortOrder);
        PageInfo<DeliveryStats> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    /** 逗号分隔的月份串（如 "2026-06,2026-07"）→ 过滤空串后的列表；null/空 → 空列表（查全部） */
    private List<String> parseYearMonths(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(yearMonth.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 按日期区间实时统计超比数据（跨月料号合并为一行）。
     *
     * 语义（方案 A 精确区间统计）：
     *  - 区间覆盖的每个自然月内的 delivery_stats 记录提供「料号集合 + 属性基座」（属性取该料号最新月记录）；
     *  - 送货/免费/上机/返修数量按 record_date ∈ [startDate, endDate] 实时统计，不再读月度快照；
     *  - 机台数 = 区间覆盖各月结算机台数之和（结算机台数按月存储，无法按日切割）；
     *  - 约定比例数量/超比数量/超比金额由 applyCalculations 统一重算；
     *  - 每日明细：单月区间按日分组填充；跨月区间留空（day01-31 共 31 列放不下 34 天，且同日号会叠加）。
     *
     * 注意：合并行 id 为 null（无真实记录可回），前端区间视图只读。
     */
    public List<DeliveryStats> queryRange(Long companyId, String keyword, String category,
                                          String startDate, String endDate, String sortOrder) {
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            return java.util.List.of();
        }
        java.time.LocalDate start;
        java.time.LocalDate end;
        try {
            start = java.time.LocalDate.parse(startDate);
            end = java.time.LocalDate.parse(endDate);
        } catch (Exception e) {
            return java.util.List.of();
        }
        if (end.isBefore(start)) return java.util.List.of();

        // 1. 区间覆盖的自然月列表（如 2026-07-29 ~ 2026-08-31 → ["2026-07", "2026-08"]）
        java.time.YearMonth ym = java.time.YearMonth.from(start);
        java.time.YearMonth endYm = java.time.YearMonth.from(end);
        List<String> months = new ArrayList<>();
        while (!ym.isAfter(endYm)) {
            months.add(ym.toString());
            ym = ym.plusMonths(1);
        }

        // 2. 取区间覆盖月份的统计记录：确定料号集合 + 属性基座（固定按 id desc，保证每个料号取到最新月记录）
        List<DeliveryStats> statsList = mapper.search(companyId, keyword, category, months, "id", "desc");
        if (statsList.isEmpty()) return java.util.List.of();

        // 3. 按 (公司, 料号) 合并：属性取最新月记录（id desc 第一条即最新月）。
        //    合并键必须含 companyId：delivery_stats 数据唯一性粒度是 (material_code, year_month, company_id)，
        //    companyId 为空（查全部公司，search 的 companyId 过滤为 <if test='companyId != null'>）时，
        //    若只按料号合并，A/B 两公司同料号会被静默并成一行（保留 id 最大者）且数量按基座公司过滤。
        java.util.Map<String, DeliveryStats> merged = new java.util.LinkedHashMap<>();
        for (DeliveryStats s : statsList) {
            Long cid = s.getCompanyId() != null ? s.getCompanyId() : 1L;
            // '\u0000' 作分隔符避免料号拼接歧义（如 "1|2A" 与 "12|A"）
            merged.putIfAbsent(cid + "\u0000" + s.getMaterialCode(), s);
        }

        // 4. 汇总「机台数」与「约定比例数量」：均取区间覆盖各月记录已存值之和
        //    （用户口径：机台数 = 两个月的机台数之和、比例内数量 = 两个月的比例数量之和；
        //    不复用 applyCalculations 的 machineCount × 用量 × 比例 重算，也不查结算机台数表——
        //    结算机台数表常无对应月份数据导致显示 0，而 delivery_stats 记录的机台数是录入时确认过的值）
        java.util.Map<String, Integer> machineCountSumByKey = new java.util.HashMap<>();
        java.util.Map<String, BigDecimal> agreedSumByKey = new java.util.HashMap<>();
        for (DeliveryStats s : statsList) {
            Long cid = s.getCompanyId() != null ? s.getCompanyId() : 1L;
            String key = cid + "\u0000" + s.getMaterialCode();
            if (s.getMachineCount() != null) {
                machineCountSumByKey.merge(key, s.getMachineCount(), Integer::sum);
            }
            if (s.getAgreedRatioQuantity() != null) {
                agreedSumByKey.merge(key, s.getAgreedRatioQuantity(), BigDecimal::add);
            }
        }

        // 5. 对每个料号按日期区间实时统计
        List<DeliveryStats> result = new ArrayList<>(merged.size());
        for (DeliveryStats base : merged.values()) {
            String code = base.getMaterialCode();
            Long cid = base.getCompanyId() != null ? base.getCompanyId() : 1L;

            DeliveryStats row = new DeliveryStats();
            row.setId(null); // 合并行无真实 id
            row.setCompanyId(cid);
            // 属性基座：取最新月记录的值
            row.setCategory(base.getCategory());
            row.setMaterialCode(code);
            row.setSystemName(base.getSystemName());
            row.setPartName(base.getPartName());
            row.setUnitUsage(base.getUnitUsage());
            row.setRatio(base.getRatio());
            row.setUnitPriceWithTax(base.getUnitPriceWithTax());
            row.setStatDate(base.getStatDate());
            // 机台数：区间覆盖各月记录存值之和（用户口径：机台数 = 两个月的机台数之和）
            row.setMachineCount(machineCountSumByKey.getOrDefault(cid + "\u0000" + code, 0));

            // 数量字段：按 record_date ∈ [start, end] 实时统计
            row.setDeliveryQuantity(deliveryRecordMapper.countByMaterialCodeAndDateRange(code, startDate, endDate, cid));
            row.setFreeDeliveryQuantity(deliveryRecordMapper.countFreeByMaterialCodeAndDateRange(code, startDate, endDate, cid));
            row.setMachineOnQuantity(originalRecordMapper.countByMaterialCodeAndDateRange(code, startDate, endDate, cid));
            row.setMonthRepair(unwarrantedMaterialMapper.countRepairByMaterialCodeAndDateRange(code, startDate, endDate, cid));

            // 派生字段：约定比例数量取各月记录存值之和；超比数量/金额按此重算，
            // 公式与 applyCalculations 一致（超比 = max(0, 上机 - 返修 - 约定比例)；
            // 超比含税金额 = 含税单价 × 超比数量 / 1.13），不调用 applyCalculations 以免其
            // 用「最新月机台数 × 用量 × 比例」覆盖约定比例之和。
            BigDecimal agreed = agreedSumByKey.getOrDefault(cid + "\u0000" + code, BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            row.setAgreedRatioQuantity(agreed);
            int machineOn = row.getMachineOnQuantity() != null ? row.getMachineOnQuantity() : 0;
            int repair = row.getMonthRepair() != null ? row.getMonthRepair() : 0;
            BigDecimal excessVal = BigDecimal.valueOf(machineOn - repair).subtract(agreed);
            row.setExcessQuantity(excessVal.compareTo(BigDecimal.ZERO) > 0 ? excessVal : BigDecimal.ZERO);
            if (row.getExcessQuantity() != null && row.getUnitPriceWithTax() != null) {
                row.setExcessAmountWithTax(
                        row.getUnitPriceWithTax().multiply(row.getExcessQuantity())
                                .divide(BigDecimal.valueOf(1.13), 2, RoundingMode.HALF_UP));
            }
            row.setYearMonth(String.join(" ~ ", months)); // 合并行年月显示为区间覆盖月份串

            // 每日明细：单月区间按日分组填充（同日号不冲突）；跨月区间留空
            if (months.size() == 1) {
                java.util.List<java.util.Map<String, Object>> dailyCounts =
                        deliveryRecordMapper.countDailyByMaterialCodeAndDateRange(code, startDate, endDate, cid);
                for (java.util.Map<String, Object> d : dailyCounts) {
                    Number day = (Number) d.get("day");
                    Number cnt = (Number) d.get("cnt");
                    if (day != null && cnt != null) {
                        setDayValue(row, day.intValue(), java.math.BigDecimal.valueOf(cnt.intValue()));
                    }
                }
            }
            result.add(row);
        }
        // 输出排序：合并行顺序与基础记录查询顺序一致（默认 id desc，最新月在前）。
        // sortOrder=asc 时翻转（导出场景：用户要求与列表相反的排序）。
        if ("asc".equalsIgnoreCase(sortOrder)) {
            java.util.Collections.reverse(result);
        }
        return result;
    }

    public DeliveryStats getById(Long id) {
        DeliveryStats r = mapper.findById(id);
        if (r == null) throw new BizException("记录不存在");
        return r;
    }

    public List<DeliveryStatsDaily> getDailies(Long statId) {
        return dailyMapper.findByStatId(statId);
    }

    @Transactional
    public DeliveryStats create(DeliveryStats record, List<DeliveryStatsDaily> dailies) {
        // 公司兜底：未传时默认归属公司 1（与其他模块一致），防止 company_id NULL 入库
        if (record.getCompanyId() == null) record.setCompanyId(1L);
        // 先按统计日期推导派生字段（yearMonth 等），保证唯一性查重与入库口径一致：
        // 前端表单在"编辑/复制后再新增"时会残留旧 yearMonth 随请求体上传，
        // 查重若用请求体值会查错月份（误报已存在或漏拦当月重复），统一按 statDate 推导
        applyCalculations(record);
        // 料号+月份唯一性校验（公司内，防止跨公司误拒）
        if (record.getMaterialCode() != null && !record.getMaterialCode().isBlank()
                && record.getYearMonth() != null && !record.getYearMonth().isBlank()) {
            if (mapper.countByMaterialCodeAndYearMonth(record.getMaterialCode(), record.getYearMonth(), record.getCompanyId()) > 0) {
                throw new BizException("该月已存在料号 '" + record.getMaterialCode() + "' 的统计记录");
            }
        }
        String user = ServiceHelper.getCurrentUserName();
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        mapper.insert(record);
        if (dailies != null && !dailies.isEmpty()) {
            for (DeliveryStatsDaily d : dailies) {
                d.setStatId(record.getId());
            }
            dailyMapper.batchInsert(dailies);
        }
        logService.log("INSERT", "delivery_stats", record.getId(), record.getCompanyId(), record.toString());
        return record;
    }

    @Transactional
    public DeliveryStats update(DeliveryStats record, List<DeliveryStatsDaily> dailies) {
        DeliveryStats exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        applyCalculations(record);
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        mapper.update(record);
        // 先删后插每日明细
        dailyMapper.deleteByStatId(record.getId());
        if (dailies != null && !dailies.isEmpty()) {
            for (DeliveryStatsDaily d : dailies) {
                d.setStatId(record.getId());
            }
            dailyMapper.batchInsert(dailies);
        }
        logService.log("UPDATE", "delivery_stats", record.getId(), record.getCompanyId(), record.toString());
        return record;
    }

    @Transactional
    public void delete(Long id) {
        DeliveryStats exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        dailyMapper.deleteByStatId(id);
        mapper.deleteById(id);
        logService.log("DELETE", "delivery_stats", id, exist.getCompanyId(), null);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        List<DeliveryStats> exists = new ArrayList<>(ids.size());
        for (Long id : ids) {
            DeliveryStats exist = getById(id);
            ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            exists.add(exist);
        }
        for (DeliveryStats exist : exists) {
            dailyMapper.deleteByStatId(exist.getId());
        }
        mapper.batchDelete(ids);
        for (DeliveryStats exist : exists) {
            logService.log("DELETE", "delivery_stats", exist.getId(), exist.getCompanyId(), null);
        }
    }

    // =============== Excel 导入 ===============
    private static final int IMPORT_BATCH_SIZE = 500;

    /**
     * 批量导入 Excel 数据（含每日明细）
     */
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<DeliveryStats> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        List<java.util.Map<Integer, BigDecimal>> dailyBatch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0}; // total, success, fail

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, DeliveryStats.class, new AnalysisEventListener<DeliveryStats>() {
                @Override
                public void invoke(DeliveryStats data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        String user = ServiceHelper.getCurrentUserName();
                        data.setCompanyId(companyId != null ? companyId : 1L);
                        data.setCreatedBy(user);
                        data.setUpdatedBy(user);
                        // Handle percentage
                        if (data.getRatio() != null) {
                            BigDecimal r = data.getRatio();
                            if (r.compareTo(BigDecimal.ONE) > 0) {
                                data.setRatio(r.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                            }
                        }
        // 机台数自动计算：Excel 未提供机台数时，按料号+月份从结算机台数查询
        // （与手动录入 autoFill 口径一致：SUM(settlement_machine_count)，查不到置 0）。
        // 必须在 applyCalculations 之前：约定比例数量依赖机台数。
        if (data.getMachineCount() == null && data.getMaterialCode() != null
                && !data.getMaterialCode().isBlank() && data.getStatDate() != null) {
            String ym = data.getStatDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            Integer mc = settlementMachineMapper.sumMachineCountByMaterialCodeAndMonth(
                    data.getMaterialCode(), ym, data.getCompanyId());
            data.setMachineCount(mc != null ? mc : 0);
        }
        // 送货免费自动计算：Excel 未提供时按料号+月份统计产品属性为"免费"的送货数量
        // （与手动录入 autoFill 口径一致）
        if (data.getFreeDeliveryQuantity() == null && data.getMaterialCode() != null
                && !data.getMaterialCode().isBlank() && data.getStatDate() != null) {
            String ym = data.getStatDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            data.setFreeDeliveryQuantity(deliveryRecordMapper.countFreeByMaterialCodeAndMonth(
                    data.getMaterialCode(), ym, data.getCompanyId()));
        }
        // 当月返修自动计算：Excel 未提供时按料号+月份统计未过保物料（与 autoFill/定时刷新口径一致）
        if (data.getMonthRepair() == null && data.getMaterialCode() != null
                && !data.getMaterialCode().isBlank() && data.getStatDate() != null) {
            String ym = data.getStatDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            data.setMonthRepair(unwarrantedMaterialMapper.countRepairByMaterialCodeAndMonth(
                    data.getMaterialCode(), ym, data.getCompanyId()));
        }
                        // 派生字段统一重算（年月/约定比例数量/超比数量/超比含税金额），
                        // 与新增/编辑/批量刷新口径一致，防止 Excel 原值或空值入库
                        applyCalculations(data);
                        // 提取每日明细
                        java.util.Map<Integer, BigDecimal> dailies = getDayValues(data);
                        batch.add(data);
                        dailyBatch.add(dailies);

                        if (batch.size() >= IMPORT_BATCH_SIZE) {
                            flushBatchWithDailies(batch, dailyBatch, counts);
                        }
                    } catch (Exception e) {
                        failDetails.add(new ImportResultDTO.FailDetail(counts[0], e.getMessage()));
                        counts[2]++;
                    }
                }
                @Override
                public void doAfterAllAnalysed(AnalysisContext ctx) {
                    if (!batch.isEmpty()) {
                        flushBatchWithDailies(batch, dailyBatch, counts);
                    }
                }
            }).sheet().doRead();
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage());
        }

        ImportResultDTO result = new ImportResultDTO();
        result.setTotal(counts[0]);
        result.setSuccess(counts[1]);
        result.setFail(counts[2]);
        result.setFailDetails(failDetails);
        return result;
    }

    /** 批量写入并处理每日明细 */
    private void flushBatchWithDailies(List<DeliveryStats> batch,
                                        List<java.util.Map<Integer, BigDecimal>> dailyBatch,
                                        int[] counts) {
        // 记录插入前的最大ID，用于反查刚插入的记录
        Long maxIdBefore = mapper.findMaxId();
        mapper.batchInsert(batch);
        counts[1] += batch.size();

        // 反查刚插入的记录并插入每日明细
        List<DeliveryStats> inserted = mapper.findByIdGreaterThan(maxIdBefore != null ? maxIdBefore : 0L);
        for (int i = 0; i < batch.size() && i < dailyBatch.size(); i++) {
            java.util.Map<Integer, BigDecimal> dailies = dailyBatch.get(i);
            if (dailies != null && !dailies.isEmpty()) {
                // 匹配：刚插入的记录按ID排，第 i 条 batch 对应 inserted 中某个位置的记录
                DeliveryStats matched = findMatch(batch.get(i), inserted);
                if (matched != null && matched.getId() != null) {
                    List<DeliveryStatsDaily> list = new ArrayList<>();
                    for (var entry : dailies.entrySet()) {
                        DeliveryStatsDaily d = new DeliveryStatsDaily();
                        d.setStatId(matched.getId());
                        d.setDayNumber(entry.getKey());
                        d.setValue(entry.getValue());
                        list.add(d);
                    }
                    if (!list.isEmpty()) dailyMapper.batchInsert(list);
                }
            }
        }
        batch.clear();
        dailyBatch.clear();
    }

    /** 根据 materialCode + yearMonth 匹配刚插入的记录 */
    private DeliveryStats findMatch(DeliveryStats target, List<DeliveryStats> candidates) {
        for (DeliveryStats c : candidates) {
            if (java.util.Objects.equals(c.getMaterialCode(), target.getMaterialCode())
                    && java.util.Objects.equals(c.getYearMonth(), target.getYearMonth())) {
                return c;
            }
        }
        return null;
    }

    // =============== Excel 导出 ===============
    /**
     * 导出超比统计。startDate/endDate 同时非空时走「区间实时统计合并导出」（跨月料号合并为一行，
     * 单月区间带每日明细、跨月区间明细留空）；否则走原按月份筛选逐条导出。
     */
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword,
                            String category, String yearMonth, String startDate, String endDate) {
        try {
            boolean rangeMode = startDate != null && !startDate.isBlank()
                    && endDate != null && !endDate.isBlank();
            List<DeliveryStats> list;
            if (rangeMode) {
                // 导出排序与列表相反（asc：合并行按 id 正序输出）
                list = queryRange(companyId, keyword, category, startDate, endDate, "asc");
            } else {
                PageHelper.startPage(1, 0); // 0 disables paging
                list = mapper.search(companyId, keyword, category, parseYearMonths(yearMonth), "id", "asc");
            }

            // 批量查询每日明细并填充到实体 transient 字段。
            // 区间合并行 id 为 null：queryRange 已在单月区间填充明细，跨月区间明细留空，无需查库。
            for (DeliveryStats s : list) {
                if (s.getId() == null) continue;
                List<DeliveryStatsDaily> dailies = dailyMapper.findByStatId(s.getId());
                for (DeliveryStatsDaily d : dailies) {
                    setDayValue(s, d.getDayNumber(), d.getValue());
                }
            }

            // 计算汇总金额（与前端逻辑一致：Σ(含税单价 × 数量) ÷ 1.13 ÷ 10000，单位为万元）
            final BigDecimal DIVISOR = new BigDecimal("11300"); // 1.13 * 10000
            BigDecimal deliveryAmount = BigDecimal.ZERO;
            BigDecimal machineOnAmount = BigDecimal.ZERO;
            BigDecimal repairAmount = BigDecimal.ZERO;
            BigDecimal agreedRatioAmount = BigDecimal.ZERO;
            BigDecimal excessAmount = BigDecimal.ZERO;
            BigDecimal excessTaxAmount = BigDecimal.ZERO;
            for (DeliveryStats s : list) {
                BigDecimal unitPrice = s.getUnitPriceWithTax() != null ? s.getUnitPriceWithTax() : BigDecimal.ZERO;
                deliveryAmount = deliveryAmount.add(unitPrice.multiply(
                        BigDecimal.valueOf(s.getDeliveryQuantity() != null ? s.getDeliveryQuantity() : 0)));
                machineOnAmount = machineOnAmount.add(unitPrice.multiply(
                        BigDecimal.valueOf(s.getMachineOnQuantity() != null ? s.getMachineOnQuantity() : 0)));
                repairAmount = repairAmount.add(unitPrice.multiply(
                        BigDecimal.valueOf(s.getMonthRepair() != null ? s.getMonthRepair() : 0)));
                agreedRatioAmount = agreedRatioAmount.add(unitPrice.multiply(
                        s.getAgreedRatioQuantity() != null ? s.getAgreedRatioQuantity() : BigDecimal.ZERO));
                excessAmount = excessAmount.add(unitPrice.multiply(
                        s.getExcessQuantity() != null ? s.getExcessQuantity() : BigDecimal.ZERO));
                excessTaxAmount = excessTaxAmount.add(
                        s.getExcessAmountWithTax() != null ? s.getExcessAmountWithTax() : BigDecimal.ZERO);
            }
            final BigDecimal[] totals = {
                    deliveryAmount.divide(DIVISOR, 2, RoundingMode.HALF_UP),
                    machineOnAmount.divide(DIVISOR, 2, RoundingMode.HALF_UP),
                    repairAmount.divide(DIVISOR, 2, RoundingMode.HALF_UP),
                    agreedRatioAmount.divide(DIVISOR, 2, RoundingMode.HALF_UP),
                    excessAmount.divide(DIVISOR, 2, RoundingMode.HALF_UP),
                    excessTaxAmount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP)
            };
            final String[] labels = {"送货金额合计", "上机金额合计", "返修金额合计", "比例内金额合计", "超比金额合计", "超比含税总额"};
            final int[] valueCols = {8, 9, 10, 11, 12, 13};
            final int dataRowCount = list.size();

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("送货统计导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, DeliveryStats.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .registerWriteHandler(new CellWriteHandler() {
                        @Override
                        public void afterCellDispose(CellWriteHandlerContext context) {
                            // 数据行从 row 1 开始（row 0 是表头），在最后一条数据的最后一个单元格写完后追加汇总行
                            if (dataRowCount > 0 && context.getRowIndex() == dataRowCount && context.getColumnIndex() == 46) {
                                Sheet sheet = context.getWriteSheetHolder().getSheet();
                                // 空两行
                                sheet.createRow(dataRowCount + 1);
                                sheet.createRow(dataRowCount + 2);
                                // 加粗样式
                                CellStyle style = sheet.getWorkbook().createCellStyle();
                                Font font = sheet.getWorkbook().createFont();
                                font.setFontHeightInPoints((short) 11);
                                font.setBold(true);
                                style.setFont(font);
                                // 第 1 行：标签
                                Row labelRow = sheet.createRow(dataRowCount + 3);
                                for (int i = 0; i < labels.length; i++) {
                                    Cell cell = labelRow.createCell(valueCols[i]);
                                    cell.setCellValue(labels[i]);
                                    cell.setCellStyle(style);
                                }
                                // 第 2 行：值
                                Row valueRow = sheet.createRow(dataRowCount + 4);
                                for (int i = 0; i < totals.length; i++) {
                                    Cell cell = valueRow.createCell(valueCols[i]);
                                    cell.setCellValue(totals[i].doubleValue());
                                    cell.setCellStyle(style);
                                }
                            }
                        }
                    })
                    .sheet("送货统计")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("导出失败: " + e.getMessage());
        }
    }

    /** 设置每日明细到 DeliveryStats 的 transient 字段 */
    private void setDayValue(DeliveryStats s, int day, BigDecimal value) {
        if (value == null) return;
        switch (day) {
            case 1: s.setDay01(value); break; case 2: s.setDay02(value); break;
            case 3: s.setDay03(value); break; case 4: s.setDay04(value); break;
            case 5: s.setDay05(value); break; case 6: s.setDay06(value); break;
            case 7: s.setDay07(value); break; case 8: s.setDay08(value); break;
            case 9: s.setDay09(value); break; case 10: s.setDay10(value); break;
            case 11: s.setDay11(value); break; case 12: s.setDay12(value); break;
            case 13: s.setDay13(value); break; case 14: s.setDay14(value); break;
            case 15: s.setDay15(value); break; case 16: s.setDay16(value); break;
            case 17: s.setDay17(value); break; case 18: s.setDay18(value); break;
            case 19: s.setDay19(value); break; case 20: s.setDay20(value); break;
            case 21: s.setDay21(value); break; case 22: s.setDay22(value); break;
            case 23: s.setDay23(value); break; case 24: s.setDay24(value); break;
            case 25: s.setDay25(value); break; case 26: s.setDay26(value); break;
            case 27: s.setDay27(value); break; case 28: s.setDay28(value); break;
            case 29: s.setDay29(value); break; case 30: s.setDay30(value); break;
            case 31: s.setDay31(value); break;
        }
    }

    /** 从 transient 字段读取每日明细 */
    private java.util.Map<Integer, BigDecimal> getDayValues(DeliveryStats s) {
        java.util.Map<Integer, BigDecimal> map = new java.util.LinkedHashMap<>();
        BigDecimal[] days = {s.getDay01(), s.getDay02(), s.getDay03(), s.getDay04(), s.getDay05(),
            s.getDay06(), s.getDay07(), s.getDay08(), s.getDay09(), s.getDay10(),
            s.getDay11(), s.getDay12(), s.getDay13(), s.getDay14(), s.getDay15(),
            s.getDay16(), s.getDay17(), s.getDay18(), s.getDay19(), s.getDay20(),
            s.getDay21(), s.getDay22(), s.getDay23(), s.getDay24(), s.getDay25(),
            s.getDay26(), s.getDay27(), s.getDay28(), s.getDay29(), s.getDay30(), s.getDay31()};
        for (int i = 0; i < days.length; i++) {
            if (days[i] != null) map.put(i + 1, days[i]);
        }
        return map;
    }

    // =============== 模板下载 ===============
    public void downloadTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("送货统计导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            DeliveryStats template = new DeliveryStats();
            template.setCategory("示例类别");
            template.setMaterialCode("示例编码");
            template.setSystemName("示例系统");
            template.setPartName("示例零件");
            template.setUnitUsage(BigDecimal.ONE);
            template.setRatio(BigDecimal.ONE);
            template.setUnitPriceWithTax(BigDecimal.ZERO);
            template.setMachineCount(1);
            template.setDeliveryQuantity(0);
            template.setFreeDeliveryQuantity(0);
            template.setMachineOnQuantity(0);
            template.setMonthRepair(0);
            template.setAgreedRatioQuantity(BigDecimal.ZERO);
            template.setExcessQuantity(BigDecimal.ZERO);
            template.setExcessAmountWithTax(BigDecimal.ZERO);
            template.setStatDate(LocalDate.now());
            template.setYearMonth("2026-07");

            List<DeliveryStats> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, DeliveryStats.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("送货统计")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }

    /**
     * 派生字段统一计算：年月（由统计日期生成）、约定比例数量、超比数量、超比含税金额。
     * 供新增/编辑/批量刷新/Excel 导入使用；DeliveryStatsScheduler 定时刷新也复用本方法，
     * 保证所有入口计算口径一致（历史教训：Scheduler 曾自带一份漏减约定比例的副本导致口径漂移）。
     *
     * 注意：ratio 的比例→小数转换（如 15 → 0.15）只允许在数据入口做一次——
     * Excel 导入由 importExcel 预处理、手动录入由前端提交时 /100；
     * 本方法【不再】转换 ratio（曾在此除 100 导致 >100% 的比例被双重除、批量刷新非幂等，错 100 倍）。
     * 本方法必须幂等：对库中已存小数（如 0.15）重复调用结果不变。
     */
    public void applyCalculations(DeliveryStats record) {
        // 年月根据统计日期自动生成（如 2026-07-28 → 2026-07），覆盖前端传入值，保证一致性
        if (record.getStatDate() != null) {
            record.setYearMonth(record.getStatDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        }
        // 约定比例数量 = 机台数 × 单台机用量 × 比例
        if (record.getMachineCount() != null && record.getUnitUsage() != null && record.getRatio() != null) {
            record.setAgreedRatioQuantity(
                    record.getUnitUsage()
                            .multiply(record.getRatio())
                            .multiply(BigDecimal.valueOf(record.getMachineCount()))
                            .setScale(2, RoundingMode.HALF_UP)
            );
        }
        // 超比数量合计 = max(0, 上机数量 - 当月返修 - 约定比例数量)
        int machineOn = record.getMachineOnQuantity() != null ? record.getMachineOnQuantity() : 0;
        int repair = record.getMonthRepair() != null ? record.getMonthRepair() : 0;
        BigDecimal agreed = record.getAgreedRatioQuantity() != null ? record.getAgreedRatioQuantity() : BigDecimal.ZERO;
        BigDecimal val = BigDecimal.valueOf(machineOn - repair).subtract(agreed);
        record.setExcessQuantity(val.compareTo(BigDecimal.ZERO) > 0 ? val : BigDecimal.ZERO);
        // 超比含税金额合计 = (含税单价 × 超比数量) / 1.13
        if (record.getExcessQuantity() != null && record.getUnitPriceWithTax() != null) {
            record.setExcessAmountWithTax(
                    record.getUnitPriceWithTax()
                            .multiply(record.getExcessQuantity())
                            .divide(BigDecimal.valueOf(1.13), 2, RoundingMode.HALF_UP)
            );
        }
    }

    /**
     * 根据料号+日期自动查询各字段的填充值
     */
    public java.util.Map<String, Object> autoFill(String materialCode, String statDate, Long companyId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (materialCode == null || materialCode.isBlank()) return result;

        // 1. 从156项表查询基础信息（公司内，防止取到其他公司的 156 项单价/比例）
        com.metal.entity.BaseMaterial156 item = companyId != null
                ? baseMaterial156Mapper.findByMaterialCodeAndCompany(materialCode, companyId)
                : baseMaterial156Mapper.findByMaterialCode(materialCode);
        if (item != null) {
            java.util.Map<String, Object> from156 = new java.util.LinkedHashMap<>();
            from156.put("category", item.getCategory());
            from156.put("systemName", item.getSystemName());
            from156.put("partName", item.getAccessory());
            from156.put("unitUsage", item.getUnitUsage());
            from156.put("ratio", item.getRatio());
            from156.put("unitPriceWithTax", item.getUnitPriceWithTax());
            result.put("from156", from156);
        }

        // 2. 获取月份
        String month = "";
        if (statDate != null && !statDate.isBlank()) {
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(statDate);
                month = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (Exception ignored) {}
        }

        if (!month.isEmpty()) {
            // 3. 机台数（来自结算机台数）
            Integer mc = settlementMachineMapper.sumMachineCountByMaterialCodeAndMonth(materialCode, month, companyId);
            result.put("machineCount", mc != null ? mc : 0);

            // 4. 送货数量
            int dq = deliveryRecordMapper.countByMaterialCodeAndMonth(materialCode, month, companyId);
            result.put("deliveryQuantity", dq);

            // 5. 送货免费（产品属性为"免费"的送货数量）
            int fdq = deliveryRecordMapper.countFreeByMaterialCodeAndMonth(materialCode, month, companyId);
            result.put("freeDeliveryQuantity", fdq);

            // 6. 上机数量
            int moq = originalRecordMapper.countByMaterialCodeAndMonth(materialCode, month, companyId);
            result.put("machineOnQuantity", moq);

            // 7. 当月返修（未过保物料：料号+月份+未过保，与 Excel 原表口径一致）
            int mr = unwarrantedMaterialMapper.countRepairByMaterialCodeAndMonth(materialCode, month, companyId);
            result.put("monthRepair", mr);

            // 7. 每日送货明细
            java.util.List<java.util.Map<String, Object>> dailyCounts =
                    deliveryRecordMapper.countDailyByMaterialCodeAndMonth(materialCode, month, companyId);
            java.util.Map<Integer, Integer> dayMap = new java.util.HashMap<>();
            for (java.util.Map<String, Object> row : dailyCounts) {
                Number day = (Number) row.get("day");
                Number cnt = (Number) row.get("cnt");
                if (day != null && cnt != null) {
                    dayMap.put(day.intValue(), cnt.intValue());
                }
            }
            int daysInMonth = java.time.YearMonth.parse(month).lengthOfMonth();
            java.util.List<java.util.Map<String, Object>> dailies = new java.util.ArrayList<>();
            for (int d = 1; d <= daysInMonth; d++) {
                java.util.Map<String, Object> daily = new java.util.LinkedHashMap<>();
                daily.put("day", d);
                daily.put("count", dayMap.getOrDefault(d, 0));
                dailies.add(daily);
            }
            result.put("dailyQuantities", dailies);
        }

        return result;
    }

    /**
     * 批量刷新指定月份的所有超比统计数据
     */
    @Transactional
    public int batchRefreshByMonth(String yearMonth, String statMonth, Long companyId) {
        if (yearMonth == null || yearMonth.isBlank()) return 0;
        List<DeliveryStats> statsList = mapper.findByYearMonth(yearMonth, companyId);
        int count = 0;
        for (DeliveryStats stats : statsList) {
            String materialCode = stats.getMaterialCode();
            if (materialCode == null || materialCode.isBlank()) continue;

            String month = statMonth != null ? statMonth : yearMonth;
            // 如果 month 是 FY2607 格式，需要转为 yyyy-MM
            if (month != null && month.startsWith("FY")) {
                try {
                    int fyYear = Integer.parseInt(month.substring(2, 4));
                    int fyMonth = Integer.parseInt(month.substring(4, 6));
                    month = String.format("20%02d-%02d", fyYear, fyMonth);
                } catch (Exception ignored) {}
            }

            int deliveryQty = deliveryRecordMapper.countByMaterialCodeAndMonth(materialCode, month, companyId);
            int freeDeliveryQty = deliveryRecordMapper.countFreeByMaterialCodeAndMonth(materialCode, month, companyId);
            int machineOnQty = originalRecordMapper.countByMaterialCodeAndMonth(materialCode, month, companyId);
            int repairQty = unwarrantedMaterialMapper.countRepairByMaterialCodeAndMonth(materialCode, month, companyId);

            stats.setDeliveryQuantity(deliveryQty);
            stats.setFreeDeliveryQuantity(freeDeliveryQty);
            stats.setMachineOnQuantity(machineOnQty);
            stats.setMonthRepair(repairQty);

            applyCalculations(stats);
            mapper.update(stats);

            // 刷新每日明细
            dailyMapper.deleteByStatId(stats.getId());
            java.util.List<java.util.Map<String, Object>> dailyCounts =
                    deliveryRecordMapper.countDailyByMaterialCodeAndMonth(materialCode, month, companyId);
            java.util.Map<Integer, Integer> dayMap = new java.util.HashMap<>();
            for (java.util.Map<String, Object> row : dailyCounts) {
                Number day = (Number) row.get("day");
                Number cnt = (Number) row.get("cnt");
                if (day != null && cnt != null) dayMap.put(day.intValue(), cnt.intValue());
            }
            try {
                int daysInMonth = java.time.YearMonth.parse(month).lengthOfMonth();
                java.util.List<DeliveryStatsDaily> dailies = new java.util.ArrayList<>();
                for (int d = 1; d <= daysInMonth; d++) {
                    DeliveryStatsDaily daily = new DeliveryStatsDaily();
                    daily.setStatId(stats.getId());
                    daily.setDayNumber(d);
                    daily.setValue(java.math.BigDecimal.valueOf(dayMap.getOrDefault(d, 0)));
                    dailies.add(daily);
                }
                if (!dailies.isEmpty()) dailyMapper.batchInsert(dailies);
            } catch (Exception ignored) {}
            count++;
        }
        return count;
    }
}
