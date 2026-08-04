package com.metal.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.metal.common.BizException;
import com.metal.common.PageResult;
import com.metal.common.ServiceHelper;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.OriginalRecord;
import com.metal.mapper.BaseMaterial156Mapper;
import com.metal.mapper.OriginalRecordMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class OriginalRecordService {

    @Autowired
    private OriginalRecordMapper mapper;

    @Autowired
    private BaseMaterial156Mapper baseMaterial156Mapper;

    @Autowired
    private com.metal.mapper.DeliveryRecordMapper deliveryRecordMapper;

    @Autowired
    private UnwarrantedMaterialService unwarrantedMaterialService;

    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("'FY'yyMM");

    public PageResult<OriginalRecord> query(int page, int pageSize, Long companyId, String keyword,
                                             String shift, String factory,
                                             String isOutOfWarranty, String startDate, String endDate,
                                             String sortField, String sortOrder,
                                             Boolean excludeLinked) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<OriginalRecord> list = mapper.search(companyId, keyword, shift, factory, isOutOfWarranty,
                startDate, endDate, sortField, sortOrder, excludeLinked);
        PageInfo<OriginalRecord> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public OriginalRecord getById(Long id) {
        OriginalRecord r = mapper.findById(id);
        if (r == null) throw new BizException("记录不存在");
        return r;
    }

    @Transactional
    public OriginalRecord create(OriginalRecord record) {
        applyCalculations(record);
        String user = ServiceHelper.getCurrentUserName();
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        mapper.insert(record);
        // 数量 >= 1 时自动下推一条未过保物料（同事务：下推失败则维修记录一并回滚）
        if (record.getQuantity() != null && record.getQuantity() >= 1) {
            unwarrantedMaterialService.createFromOriginalRecord(record);
        }
        return record;
    }

    @Transactional
    public OriginalRecord update(OriginalRecord record) {
        OriginalRecord exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        applyCalculations(record);
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        mapper.update(record);
        // 同步已下推的未过保物料：已有关联则回填+重算；无关联且数量>=1 则下推
        unwarrantedMaterialService.pushFromOriginalRecord(record);
        return record;
    }

    @Transactional
    public void delete(Long id) {
        OriginalRecord exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        // 级联删除关联的未过保物料（前端已提示"有关联记录，删除将一并删除"）
        unwarrantedMaterialService.deleteByOriginalRecordId(id, exist.getCompanyId());
        mapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        if (!ServiceHelper.isAdmin()) {
            for (Long id : ids) {
                OriginalRecord exist = getById(id);
                ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            }
        }
        // 级联删除关联的未过保物料（前端已提示）
        for (Long id : ids) {
            OriginalRecord exist = getById(id);
            unwarrantedMaterialService.deleteByOriginalRecordId(id, exist.getCompanyId());
        }
        mapper.batchDelete(ids);
    }

    /** 某维修记录已关联的未过保物料条数（前端删除提示用） */
    public int linkedCount(Long id) {
        OriginalRecord exist = getById(id);
        return unwarrantedMaterialService.countByOriginalRecordId(id, exist.getCompanyId());
    }

    public OriginalRecord copy(Long id) {
        return getById(id);
    }

    /**
     * 根据料号查询156项表，返回配件名称（用于维修记录自动回填）
     */
    public java.util.Map<String, String> lookupFrom156(String materialCode) {
        if (materialCode == null || materialCode.isBlank()) {
            return java.util.Map.of("partName", "");
        }
        com.metal.entity.BaseMaterial156 item = baseMaterial156Mapper.findByMaterialCode(materialCode);
        if (item != null) {
            return java.util.Map.of("partName", item.getPartName() != null ? item.getPartName() : "");
        }
        return java.util.Map.of("partName", "");
    }

    /**
     * 根据上机物料号 + 日期，查询本月送货记录中序列号匹配的记录数
     */
    public java.util.Map<String, Object> lookupDeliveryRef(String machineOnMaterial, String recordDate, Long companyId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("count", 0);
        if (machineOnMaterial == null || machineOnMaterial.isBlank() || recordDate == null || recordDate.isBlank()) {
            return result;
        }
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(recordDate);
            String month = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            int count = deliveryRecordMapper.countByMaterialSerialAndMonth(machineOnMaterial, month, companyId);
            result.put("count", count);
        } catch (Exception ignored) {}
        return result;
    }

    // =============== Excel 导入 ===============
    private static final int IMPORT_BATCH_SIZE = 500;

    /**
     * 批量导入 Excel 数据
     * 采用分批 INSERT + 事务保护：每 500 条一批，中途失败自动回滚整批
     */
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<OriginalRecord> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0}; // total, success, fail

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, OriginalRecord.class, new AnalysisEventListener<OriginalRecord>() {
                @Override
                public void invoke(OriginalRecord data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        // 修复时间字段：如果年份异常（≤1900），用recordDate重新构造日期部分
                        LocalDate date = data.getRecordDate();
                        data.setRepairRequestTime(fixTime(data.getRepairRequestTime(), date));
                        data.setStartTime(fixTime(data.getStartTime(), date));
                        data.setEndTime(fixTime(data.getEndTime(), date));

                        applyCalculations(data);
                        // 导入时：如果料号为空但上机物料有值，尝试从送货记录回填料号
                        if ((data.getMaterialCode() == null || data.getMaterialCode().isBlank())
                                && data.getMachineOnMaterial() != null && !data.getMachineOnMaterial().isBlank()) {
                            try {
                                com.metal.entity.DeliveryRecord delivery = deliveryRecordMapper.findByMaterialSerial(
                                        data.getMachineOnMaterial(), companyId);
                                if (delivery != null && delivery.getMaterialCode() != null) {
                                    data.setMaterialCode(delivery.getMaterialCode());
                                    if ((data.getPartName() == null || data.getPartName().isBlank()) && delivery.getMaterialName() != null) {
                                        data.setPartName(delivery.getMaterialName());
                                    }
                                }
                            } catch (Exception ignored) {
                                // 查询失败不阻塞导入
                            }
                        }
                        // 导入时：如果送货记录引用为空但有上机物料，自动计算本月匹配数
                        if ((data.getDeliveryRecordRef() == null || data.getDeliveryRecordRef().isBlank())
                                && data.getMachineOnMaterial() != null && !data.getMachineOnMaterial().isBlank()
                                && data.getRecordDate() != null) {
                            try {
                                String month = data.getRecordDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                                int count = deliveryRecordMapper.countByMaterialSerialAndMonth(data.getMachineOnMaterial(), month, companyId);
                                if (count > 0) {
                                    data.setDeliveryRecordRef(String.valueOf(count));
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        String user = ServiceHelper.getCurrentUserName();
                        data.setCompanyId(companyId != null ? companyId : 1L);
                        data.setCreatedBy(user);
                        data.setUpdatedBy(user);
                        batch.add(data);

                        if (batch.size() >= IMPORT_BATCH_SIZE) {
                            flushBatch(batch, counts);
                        }
                    } catch (Exception e) {
                        failDetails.add(new ImportResultDTO.FailDetail(counts[0], e.getMessage()));
                        counts[2]++;
                    }
                }
                @Override
                public void doAfterAllAnalysed(AnalysisContext ctx) {
                    if (!batch.isEmpty()) {
                        flushBatch(batch, counts);
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

    private void flushBatch(List<OriginalRecord> batch, int[] counts) {
        mapper.batchInsert(batch);
        // Excel 导入的维修记录同样触发下推：数量 >= 1 时自动下推未过保物料（同事务）
        for (OriginalRecord r : batch) {
            if (r.getQuantity() != null && r.getQuantity() >= 1) {
                unwarrantedMaterialService.createFromOriginalRecord(r);
            }
        }
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword,
                            String shift, String factory,
                            String isOutOfWarranty, String startDate, String endDate) {
        try {
            PageHelper.startPage(1, 0);
            List<OriginalRecord> list = mapper.search(companyId, keyword, shift, factory, isOutOfWarranty,
                    startDate, endDate, "id", "desc", null);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("维修记录导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, OriginalRecord.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("维修记录")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("导出失败: " + e.getMessage());
        }
    }

    // =============== 模板下载 ===============
    public void downloadTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("维修记录导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OriginalRecord template = new OriginalRecord();
            template.setYearMonth("FY2607");
            template.setRecordDate(LocalDate.now());
            template.setShift("白班");
            template.setFactory("示例厂房");
            template.setSerialNumber("示例序号");
            template.setMachineNo("示例机台号");
            template.setDiagnostician("示例诊断人");
            template.setRepairPerson("示例维修人");
            template.setMachineModel("示例机型");
            template.setFaultPhenomenon("示例故障现象");
            template.setFaultDescription("示例故障描述");
            template.setMaterialCode("示例物料编码");
            template.setPartName("示例零件名称");
            template.setQuantity(1);
            template.setMachineOnMaterial("示例上机物料");
            template.setMachineOffMaterial("示例下机物料");
            template.setRemark("示例备注");
            template.setConfirmer("示例确认人");
            template.setDeliveryRecordRef("示例送货记录引用");

            List<OriginalRecord> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, OriginalRecord.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("维修记录")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }

    // =============== 过保实时查询 ===============
    public java.util.Map<String, Object> lookupWarranty(String machineOffMaterial, String recordDate) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            if (machineOffMaterial == null || machineOffMaterial.isBlank()) {
                result.put("lastMachineOnTime", null);
                result.put("isOutOfWarranty", "无");
                return result;
            }
            java.time.LocalDate baseDate = recordDate != null && !recordDate.isBlank()
                    ? java.time.LocalDate.parse(recordDate) : java.time.LocalDate.now();
            java.time.LocalDate lastTime = mapper.findLastMachineOnTime(machineOffMaterial);
            result.put("lastMachineOnTime", lastTime != null ? lastTime.toString() : null);
            if (lastTime != null) {
                long months = java.time.temporal.ChronoUnit.MONTHS.between(lastTime, baseDate);
                result.put("isOutOfWarranty", months >= 6 ? "已过保" : "未过保");
            } else {
                result.put("isOutOfWarranty", "无");
            }
        } catch (Exception e) {
            result.put("lastMachineOnTime", null);
            result.put("isOutOfWarranty", "无");
        }
        return result;
    }

    // =============== 自动计算 ===============
    /**
     * 修复 Excel 时间字段的日期异常（Excel 纯时间存储为 0~1 的序列号，
     * EasyExcel 解析后日期部分为 1899-12-31），用 recordDate 替换日期部分。
     */
    private LocalDateTime fixTime(LocalDateTime dt, LocalDate date) {
        if (dt == null || date == null) return dt;
        if (dt.getYear() <= 1900) {
            return LocalDateTime.of(date, dt.toLocalTime());
        }
        return dt;
    }

    private void applyCalculations(OriginalRecord record) {
        // 年+月
        if (record.getRecordDate() != null) {
            record.setYearMonth(record.getRecordDate().format(YM_FMT));
        }
        // 厂房+机台号（规则：厂房-机台号，两端去空格；任一为空则置空）
        if (record.getFactory() != null && record.getMachineNo() != null) {
            String f = record.getFactory().trim();
            String m = record.getMachineNo().trim();
            record.setPlantMachine(!f.isEmpty() && !m.isEmpty() ? f + "-" + m : null);
        } else {
            record.setPlantMachine(null);
        }
        // 跨天修正 + 工时计算（单位：分钟）
        LocalDateTime start = record.getStartTime();
        LocalDateTime end = record.getEndTime();
        LocalDateTime request = record.getRepairRequestTime();

        // 跨天修正：如果结束时间早于开始时间或报修时间，自动加一天
        if (end != null) {
            if (start != null && end.isBefore(start)) {
                end = end.plusDays(1);
                record.setEndTime(end);
            } else if (request != null && end.isBefore(request)) {
                end = end.plusDays(1);
                record.setEndTime(end);
            }
        }

        // 维修工时 = 结束时间 - 开始时间（分钟）
        if (start != null && end != null) {
            long minutes = Duration.between(start, end).toMinutes();
            record.setRepairHours(BigDecimal.valueOf(minutes));
        }
        // 停机工时 = 结束时间 - 报修时间（分钟）
        if (request != null && end != null) {
            long minutes = Duration.between(request, end).toMinutes();
            record.setDowntimeHours(BigDecimal.valueOf(minutes));
        }
        // 上次上机时间: 查询下机物料号上一次在上机物料列出现的日期
        if (record.getMachineOffMaterial() != null && !record.getMachineOffMaterial().isBlank()) {
            LocalDate lastTime = mapper.findLastMachineOnTime(record.getMachineOffMaterial());
            record.setLastMachineOnTime(lastTime);
            // 是否过保：以维修记录的日期为准，距上次上机是否 >= 6 个月
            if (lastTime != null && record.getRecordDate() != null) {
                long months = java.time.temporal.ChronoUnit.MONTHS.between(lastTime, record.getRecordDate());
                record.setIsOutOfWarranty(months >= 6 ? "已过保" : "未过保");
            } else {
                record.setIsOutOfWarranty("无");
            }
        } else {
            record.setIsOutOfWarranty("无");
        }
    }
}
