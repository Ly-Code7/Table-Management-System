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
import com.metal.dto.UnwarrantedMaterialComputeDTO;
import com.metal.dto.UnwarrantedMaterialLookupDTO;
import com.metal.entity.Material;
import com.metal.entity.OriginalRecord;
import com.metal.entity.UnwarrantedMaterial;
import com.metal.mapper.MaterialMapper;
import com.metal.mapper.OriginalRecordMapper;
import com.metal.mapper.UnwarrantedMaterialMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 未过保物料 Service。
 *
 * 基础字段（日期/厂房/机台号/设备维修调试/维修物料装上/维修人/未过保/配件名称/数量/物料编码/维修金额）
 * 来自"选择维修记录"回填或手动录入；
 * 派生字段（唯一标识编号、第几次、总次数、上次日期、上次/本次日期+编号、超六个月、使用时长、上次维修人）
 * 由 {@link #applyCalculations} 在新增/编辑/导入时统一重算，覆盖前端传入值，保证一致性。
 */
@Service
public class UnwarrantedMaterialService {

    @Autowired
    private UnwarrantedMaterialMapper mapper;

    @Autowired
    private OriginalRecordMapper originalRecordMapper;

    @Autowired
    private MaterialMapper materialMapper;

    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("'FY'yyMM");
    private static final int IMPORT_BATCH_SIZE = 500;

    public PageResult<UnwarrantedMaterial> query(int page, int pageSize, Long companyId, String keyword,
                                                 String factory, String warrantyStatus,
                                                 String startDate, String endDate,
                                                 String sortField, String sortOrder) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<UnwarrantedMaterial> list = mapper.search(companyId, keyword, factory, warrantyStatus,
                startDate, endDate, sortField, sortOrder);
        PageInfo<UnwarrantedMaterial> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public UnwarrantedMaterial getById(Long id) {
        UnwarrantedMaterial r = mapper.findById(id);
        if (r == null) throw new BizException("记录不存在");
        return r;
    }

    @Transactional
    public UnwarrantedMaterial create(UnwarrantedMaterial record) {
        if (record.getCompanyId() == null) record.setCompanyId(1L);
        checkOriginalRecordUniqueness(record);
        applyCalculations(record);
        String user = ServiceHelper.getCurrentUserName();
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        mapper.insert(record);
        return record;
    }

    @Transactional
    public UnwarrantedMaterial update(UnwarrantedMaterial record) {
        UnwarrantedMaterial exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        if (record.getCompanyId() == null) record.setCompanyId(exist.getCompanyId());
        checkOriginalRecordUniqueness(record);
        applyCalculations(record);
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        mapper.update(record);
        return record;
    }

    /** 校验：一个维修记录只允许被一个未过保物料记录关联（同一公司内） */
    private void checkOriginalRecordUniqueness(UnwarrantedMaterial record) {
        if (record.getOriginalRecordId() != null && record.getCompanyId() != null
                && mapper.countByOriginalRecordId(record.getOriginalRecordId(), record.getCompanyId(), record.getId()) > 0) {
            throw new BizException("该维修记录已被其他未过保物料记录关联");
        }
    }

    @Transactional
    public void delete(Long id) {
        UnwarrantedMaterial exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        mapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        if (!ServiceHelper.isAdmin()) {
            for (Long id : ids) {
                UnwarrantedMaterial exist = getById(id);
                ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            }
        }
        mapper.batchDelete(ids);
    }

    // =============== 关联维修记录回填 ===============

    /**
     * 根据维修记录 id 返回可回填的基础字段（仅限当前公司内的维修记录）。
     * 记录不存在或跨公司时返回 null，前端保持表单原值。
     */
    public UnwarrantedMaterialLookupDTO lookupOriginal(Long originalId, Long companyId) {
        if (originalId == null) return null;
        Long cid = companyId != null ? companyId : 1L;
        OriginalRecord o = originalRecordMapper.findByIdAndCompany(originalId, cid);
        if (o == null) return null;
        UnwarrantedMaterialLookupDTO dto = new UnwarrantedMaterialLookupDTO();
        dto.setOriginalRecordId(originalId);
        dto.setRecordDate(o.getRecordDate() != null ? o.getRecordDate().toString() : null);
        dto.setFactory(o.getFactory());
        dto.setMachineNo(o.getMachineNo());
        dto.setEquipRepairDebugging(o.getFaultDescription());
        dto.setRepairMaterialOn(o.getMachineOnMaterial());
        dto.setRepairPerson(o.getRepairPerson());
        // 是否过保为"无"时不留存，其余回填原值
        String w = o.getIsOutOfWarranty();
        dto.setWarrantyStatus("无".equals(w) ? "" : w);
        dto.setPartName(o.getPartName());
        dto.setQuantity(o.getQuantity());
        dto.setMaterialCode(o.getMaterialCode());
        if (o.getRecordDate() != null) {
            dto.setYearMonth(o.getRecordDate().format(YM_FMT));
        }
        return dto;
    }

    /**
     * 派生字段预览（纯读，不写库）。返回所有由前端展示的派生字段。
     */
    public UnwarrantedMaterialComputeDTO compute(String factory, String machineNo, String materialCode,
                                                 String recordDate, Long companyId, Long excludeId) {
        UnwarrantedMaterialComputeDTO dto = new UnwarrantedMaterialComputeDTO();
        try {
            UnwarrantedMaterial tmp = new UnwarrantedMaterial();
            tmp.setFactory(factory);
            tmp.setMachineNo(machineNo);
            tmp.setMaterialCode(materialCode);
            tmp.setCompanyId(companyId != null ? companyId : 1L);
            tmp.setId(excludeId);
            if (recordDate != null && !recordDate.isBlank()) {
                tmp.setRecordDate(LocalDate.parse(recordDate));
            }
            applyCalculations(tmp);
            dto.setCategory(tmp.getCategory());
            dto.setUniqueId(tmp.getUniqueId());
            dto.setPlantMachine(tmp.getPlantMachine());
            dto.setYearMonth(tmp.getYearMonth());
            dto.setCurrentDate(tmp.getCurrentDate() != null ? tmp.getCurrentDate().toString() : null);
            dto.setOccurrenceNo(tmp.getOccurrenceNo());
            dto.setTotalCount(tmp.getTotalCount());
            dto.setLastDate(tmp.getLastDate() != null ? tmp.getLastDate().toString() : null);
            dto.setLastDateNo(tmp.getLastDateNo());
            dto.setCurrentDateNo(tmp.getCurrentDateNo());
            dto.setOverSixMonths(tmp.getOverSixMonths());
            dto.setUsageMonths(tmp.getUsageMonths());
            dto.setLastRepairPerson(tmp.getLastRepairPerson());
        } catch (Exception ignored) {
            // 参数不合法时不返回派生值，前端保持原值
        }
        return dto;
    }

    // =============== Excel 导入 ===============

    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<UnwarrantedMaterial> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0}; // total, success, fail

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, UnwarrantedMaterial.class, new AnalysisEventListener<UnwarrantedMaterial>() {
                @Override
                public void invoke(UnwarrantedMaterial data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        String user = ServiceHelper.getCurrentUserName();
                        data.setCompanyId(companyId != null ? companyId : 1L);
                        data.setCreatedBy(user);
                        data.setUpdatedBy(user);
                        // 未过保字段文件没给时，按 厂房+机台号+料号+日期 反查维修记录回填
                        if (data.getWarrantyStatus() == null || data.getWarrantyStatus().isBlank()) {
                            backfillWarrantyStatus(data);
                        }
                        // 派生字段后端统一重算（覆盖文件里的派生值）
                        applyCalculations(data);
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

    /** 按 厂房+机台号+料号+日期 反查维修记录，回填未过保字段（查不到留空） */
    private void backfillWarrantyStatus(UnwarrantedMaterial data) {
        if (data.getRecordDate() == null || !notBlank(data.getFactory())
                || !notBlank(data.getMachineNo()) || !notBlank(data.getMaterialCode())) {
            data.setWarrantyStatus("");
            return;
        }
        String ds = data.getRecordDate().toString();
        List<OriginalRecord> hits = originalRecordMapper.search(
                data.getCompanyId(), data.getMaterialCode(), null, data.getFactory(),
                null, ds, ds, "id", "desc", null);
        if (!hits.isEmpty()) {
            String w = hits.get(0).getIsOutOfWarranty();
            data.setWarrantyStatus("无".equals(w) ? "" : w);
        } else {
            data.setWarrantyStatus("");
        }
    }

    private void flushBatch(List<UnwarrantedMaterial> batch, int[] counts) {
        mapper.batchInsert(batch);
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword,
                            String factory, String warrantyStatus, String startDate, String endDate) {
        try {
            PageHelper.startPage(1, 0);
            List<UnwarrantedMaterial> list = mapper.search(companyId, keyword, factory, warrantyStatus,
                    startDate, endDate, "id", "desc");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("未过保物料导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, UnwarrantedMaterial.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("未过保物料")
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
            String fileName = URLEncoder.encode("未过保物料导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            UnwarrantedMaterial template = new UnwarrantedMaterial();
            template.setRecordDate(LocalDate.now());
            template.setFactory("示例厂房");
            template.setMachineNo("示例机台号");
            template.setEquipRepairDebugging("示例设备维修调试");
            template.setRepairMaterialOn("示例维修物料装上");
            template.setRepairPerson("示例维修人");
            template.setWarrantyStatus("未过保");
            template.setPartName("示例配件名称");
            template.setQuantity(1);
            template.setMaterialCode("示例物料编码");
            template.setCategory("示例类别");
            template.setRepairAmount(java.math.BigDecimal.ZERO);

            List<UnwarrantedMaterial> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, UnwarrantedMaterial.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("未过保物料")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }

    // =============== 派生字段自动计算 ===============

    /**
     * Excel 序列号：1899-12-31 对应数值 0，1900-01-01 对应数值 1。
     * 使用 1899-12-31 为基准 + 1，可正确复现 Excel 对 1900 闰年的处理（例 2026-07-28 → 46231）。
     */
    private long serialNo(LocalDate d) {
        return ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 31), d) + 1;
    }

    /** 整月数：只统计满整月，零头天数舍弃（例 2025-01-20 ~ 2025-03-10 → 1） */
    private int wholeMonths(LocalDate from, LocalDate to) {
        int months = (to.getYear() - from.getYear()) * 12 + (to.getMonthValue() - from.getMonthValue());
        if (to.getDayOfMonth() < from.getDayOfMonth()) months--;
        return Math.max(months, 0);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 按物料编码从物料表回填类别（查不到时保留原值） */
    private void fillCategory(UnwarrantedMaterial r) {
        if (!notBlank(r.getMaterialCode()) || r.getCompanyId() == null) return;
        try {
            Material m = materialMapper.findByCode(r.getMaterialCode(), r.getCompanyId());
            if (m != null && notBlank(m.getCategory())) {
                r.setCategory(m.getCategory());
            }
        } catch (Exception ignored) {
            // 物料查询失败不阻塞
        }
    }

    /**
     * 派生字段统一计算。覆盖前端传入值，保证一致性（参照 DeliveryStatsService.applyCalculations）。
     * warrantyStatus 与 repairAmount 属于基础字段，此处不覆盖。
     */
    private void applyCalculations(UnwarrantedMaterial r) {
        fillCategory(r);
        LocalDate date = r.getRecordDate();
        if (date != null) {
            r.setCurrentDate(date);
            r.setYearMonth(date.format(YM_FMT));
        } else {
            r.setCurrentDate(null);
            r.setYearMonth(null);
        }

        boolean hasKey = notBlank(r.getFactory()) && notBlank(r.getMachineNo()) && notBlank(r.getMaterialCode());
        String uniqueId = null;
        if (hasKey) {
            // 唯一标识编号：厂房-机台物料编码（机台号与物料编码之间无分隔符，如 B5-H1115300812-00）
            uniqueId = r.getFactory() + "-" + r.getMachineNo() + r.getMaterialCode();
            r.setUniqueId(uniqueId);
            r.setPlantMachine(ServiceHelper.combineFactoryMachine(r.getFactory(), r.getMachineNo()));
        } else {
            r.setUniqueId(null);
            r.setPlantMachine(null);
        }

        if (!hasKey || date == null || r.getCompanyId() == null) {
            r.setOccurrenceNo(null);
            r.setTotalCount(null);
            r.setLastDate(null);
            r.setLastDateNo(null);
            r.setCurrentDateNo(null);
            r.setOverSixMonths(null);
            r.setUsageMonths(null);
            r.setLastRepairPerson(null);
            return;
        }

        Long cid = r.getCompanyId();
        Long excludeId = r.getId(); // create 时为 null，update 时为当前 id（排除自身）
        int occurrence = mapper.countByUniqueId(uniqueId, cid, excludeId) + 1;
        r.setOccurrenceNo(occurrence);
        r.setTotalCount(occurrence);

        UnwarrantedMaterial prev = mapper.findLatestByUniqueId(uniqueId, cid, excludeId);
        if (prev != null) {
            r.setLastDate(prev.getRecordDate());
            r.setLastRepairPerson(occurrence > 1 ? prev.getRepairPerson() : null);
        } else {
            r.setLastDate(null);
            r.setLastRepairPerson(null);
        }

        long serial = serialNo(date);
        r.setCurrentDateNo(serial + uniqueId);
        r.setLastDateNo(r.getLastDate() != null ? serialNo(r.getLastDate()) + uniqueId : null);

        if (r.getLastDate() == null) {
            r.setOverSixMonths("1st");
            r.setUsageMonths("1st");
        } else {
            r.setOverSixMonths(ChronoUnit.MONTHS.between(r.getLastDate(), date) >= 6 ? "Y" : "N");
            r.setUsageMonths(String.valueOf(wholeMonths(r.getLastDate(), date)));
        }
    }
}
