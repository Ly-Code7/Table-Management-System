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
import com.metal.entity.MachineMaterial;
import com.metal.mapper.MachineMaterialMapper;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MachineMaterialService {

    @Autowired
    private MachineMaterialMapper mapper;

    @Autowired
    private com.metal.mapper.DeliveryRecordMapper deliveryRecordMapper;

    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("'FY'yyMM");

    public PageResult<MachineMaterial> query(int page, int pageSize, Long companyId, String keyword,
                                              String factory, String isOutOfWarranty,
                                              String startDate, String endDate,
                                              String sortField, String sortOrder) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<MachineMaterial> list = mapper.search(companyId, keyword, factory, isOutOfWarranty,
                startDate, endDate, sortField, sortOrder);
        PageInfo<MachineMaterial> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public MachineMaterial getById(Long id) {
        MachineMaterial r = mapper.findById(id);
        if (r == null) throw new BizException("记录不存在");
        return r;
    }

    @Transactional
    public MachineMaterial create(MachineMaterial record) {
        applyCalculations(record);
        String user = ServiceHelper.getCurrentUserName();
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        mapper.insert(record);
        return record;
    }

    @Transactional
    public MachineMaterial update(MachineMaterial record) {
        MachineMaterial exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        applyCalculations(record);
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        mapper.update(record);
        return record;
    }

    @Transactional
    public void delete(Long id) {
        MachineMaterial exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        mapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        if (!ServiceHelper.isAdmin()) {
            for (Long id : ids) {
                MachineMaterial exist = getById(id);
                ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            }
        }
        mapper.batchDelete(ids);
    }

    // =============== Excel 导入 ===============
    private static final int IMPORT_BATCH_SIZE = 500;

    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<MachineMaterial> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0}; // total, success, fail

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, MachineMaterial.class, new AnalysisEventListener<MachineMaterial>() {
                @Override
                public void invoke(MachineMaterial data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        applyCalculations(data);
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

    private void flushBatch(List<MachineMaterial> batch, int[] counts) {
        mapper.batchInsert(batch);
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword,
                            String factory, String isOutOfWarranty, String startDate, String endDate) {
        try {
            PageHelper.startPage(1, 0);
            List<MachineMaterial> list = mapper.search(companyId, keyword, factory, isOutOfWarranty,
                    startDate, endDate, "id", "desc");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("机台物料导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, MachineMaterial.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("机台物料")
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
            String fileName = URLEncoder.encode("机台物料导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            MachineMaterial template = new MachineMaterial();
            template.setYearMonth("示例年月");
            template.setRecordDate(LocalDate.now());
            template.setShift("示例班次");
            template.setFactory("示例厂房");
            template.setSerialNumber("示例序号");
            template.setMachineNo("示例机台号");
            template.setRepairPerson("示例维修人");
            template.setRemark("示例备注");
            template.setConfirmer("示例确认人");
            template.setMaterialCode("示例物料编码");
            template.setPartName("示例零件名称");
            template.setQuantity(1);
            template.setMachineOnMaterial("示例上机物料");
            template.setMachineOffMaterial("示例下机物料");
            template.setMachineModel("示例机型");
            template.setFaultPhenomenon("示例故障现象");
            template.setFaultDescription("示例故障描述");
            template.setDeliveryRecordRef("示例送货记录引用");
            template.setIsOutOfWarranty("示例是否过保");

            List<MachineMaterial> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, MachineMaterial.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("机台物料")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }

    // =============== 过保实时查询 ===============
    public java.util.Map<String, Object> lookupWarranty(String machineOffMaterial) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            if (machineOffMaterial == null || machineOffMaterial.isBlank()) {
                result.put("lastMachineOnTime", null);
                result.put("isOutOfWarranty", "无");
                return result;
            }
            java.time.LocalDate lastTime = mapper.findLastMachineOnTime(machineOffMaterial);
            result.put("lastMachineOnTime", lastTime != null ? lastTime.toString() : null);
            if (lastTime != null) {
                long months = java.time.temporal.ChronoUnit.MONTHS.between(lastTime, java.time.LocalDate.now());
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

    // =============== 上机物料查询送货记录 ===============
    /**
     * 根据填写的上机物料号（对应送货记录中的物料序列号），
     * 查询送货记录表，回填对应的物料编码和送货记录引用
     */
    public java.util.Map<String, Object> lookupDeliveryByMaterial(String machineOnMaterial) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (machineOnMaterial == null || machineOnMaterial.isBlank()) {
            result.put("materialCode", null);
            result.put("deliveryRecordRef", null);
            return result;
        }
        try {
            com.metal.entity.DeliveryRecord dr = deliveryRecordMapper.findByMaterialSerial(machineOnMaterial);
            if (dr != null) {
                result.put("materialCode", dr.getMaterialCode());
                // 送货记录引用格式: "送货单号(ID=xxx)"
                StringBuilder ref = new StringBuilder();
                if (dr.getShipmentNo() != null && !dr.getShipmentNo().isBlank()) {
                    ref.append(dr.getShipmentNo());
                }
                ref.append("(ID=").append(dr.getId()).append(")");
                result.put("deliveryRecordRef", ref.toString());
            } else {
                result.put("materialCode", null);
                result.put("deliveryRecordRef", null);
            }
        } catch (Exception e) {
            result.put("materialCode", null);
            result.put("deliveryRecordRef", null);
        }
        return result;
    }

    // =============== 自动计算 ===============
    private void applyCalculations(MachineMaterial record) {
        if (record.getRecordDate() != null) {
            record.setYearMonth(record.getRecordDate().format(YM_FMT));
        }
        if (record.getStartTime() != null && record.getEndTime() != null) {
            long minutes = Duration.between(record.getStartTime(), record.getEndTime()).toMinutes();
            record.setRepairHours(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        }
        if (record.getRepairRequestTime() != null && record.getEndTime() != null) {
            long minutes = Duration.between(record.getRepairRequestTime(), record.getEndTime()).toMinutes();
            record.setDowntimeHours(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        }
        if (record.getMachineOffMaterial() != null && !record.getMachineOffMaterial().isBlank()) {
            LocalDate lastTime = mapper.findLastMachineOnTime(record.getMachineOffMaterial());
            record.setLastMachineOnTime(lastTime);
            if (lastTime != null) {
                long months = java.time.temporal.ChronoUnit.MONTHS.between(lastTime, LocalDate.now());
                record.setIsOutOfWarranty(months >= 6 ? "已过保" : "未过保");
            } else {
                record.setIsOutOfWarranty("无");
            }
        } else {
            record.setIsOutOfWarranty("无");
        }
    }
}
