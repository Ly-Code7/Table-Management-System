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

    public PageResult<DeliveryStats> query(int page, int pageSize, Long companyId, String keyword,
                                            String category, String yearMonth,
                                            String sortField, String sortOrder) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<DeliveryStats> list = mapper.search(companyId, keyword, category, yearMonth, sortField, sortOrder);
        PageInfo<DeliveryStats> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
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
        // 料号+月份唯一性校验
        if (record.getMaterialCode() != null && !record.getMaterialCode().isBlank()
                && record.getYearMonth() != null && !record.getYearMonth().isBlank()) {
            if (mapper.countByMaterialCodeAndYearMonth(record.getMaterialCode(), record.getYearMonth()) > 0) {
                throw new BizException("该月已存在料号 '" + record.getMaterialCode() + "' 的统计记录");
            }
        }
        applyCalculations(record);
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
        return record;
    }

    @Transactional
    public void delete(Long id) {
        DeliveryStats exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        dailyMapper.deleteByStatId(id);
        mapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        if (!ServiceHelper.isAdmin()) {
            for (Long id : ids) {
                DeliveryStats exist = getById(id);
                ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            }
        }
        for (Long id : ids) {
            dailyMapper.deleteByStatId(id);
        }
        mapper.batchDelete(ids);
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
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword,
                            String category, String yearMonth) {
        try {
            PageHelper.startPage(1, 0); // 0 disables paging
            List<DeliveryStats> list = mapper.search(companyId, keyword, category, yearMonth, "id", "asc");

            // 批量查询每日明细并填充到实体 transient 字段
            for (DeliveryStats s : list) {
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

    private void applyCalculations(DeliveryStats record) {
        // 比例从百分比转为小数（如 15 → 0.15），与 Excel 导入逻辑一致
        if (record.getRatio() != null && record.getRatio().compareTo(BigDecimal.ONE) > 0) {
            record.setRatio(record.getRatio().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
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
        // 超比数量合计 = max(0, 送货数量 - 当月返修 - 约定比例数量)
        int delivery = record.getDeliveryQuantity() != null ? record.getDeliveryQuantity() : 0;
        int repair = record.getMonthRepair() != null ? record.getMonthRepair() : 0;
        BigDecimal agreed = record.getAgreedRatioQuantity() != null ? record.getAgreedRatioQuantity() : BigDecimal.ZERO;
        BigDecimal val = BigDecimal.valueOf(delivery - repair).subtract(agreed);
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

        // 1. 从156项表查询基础信息
        com.metal.entity.BaseMaterial156 item = baseMaterial156Mapper.findByMaterialCode(materialCode);
        if (item != null) {
            java.util.Map<String, Object> from156 = new java.util.LinkedHashMap<>();
            from156.put("category", item.getCategory());
            from156.put("systemName", item.getSystemName());
            from156.put("partName", item.getPartName());
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

            // 5. 上机数量
            int moq = originalRecordMapper.countByMaterialCodeAndMonth(materialCode, month, companyId);
            result.put("machineOnQuantity", moq);

            // 6. 当月返修（未过保）
            int mr = originalRecordMapper.countRepairByMaterialCodeAndMonth(materialCode, month, companyId);
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
            int machineOnQty = originalRecordMapper.countByMaterialCodeAndMonth(materialCode, month, companyId);
            int repairQty = originalRecordMapper.countRepairByMaterialCodeAndMonth(materialCode, month, companyId);

            stats.setDeliveryQuantity(deliveryQty);
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
