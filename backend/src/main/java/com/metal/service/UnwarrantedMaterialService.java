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
import com.metal.mapper.DeliveryStatsMapper;
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
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 未过保物料 Service。
 *
 * 基础字段（日期/厂房/机台号/处理方式/上机物料/维修人/未过保/配件名称/数量/料号/维修金额）
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

    @Autowired
    private DeliveryStatsMapper deliveryStatsMapper;

    @Autowired
    private com.metal.mapper.BaseMaterial156Mapper baseMaterial156Mapper;

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
        // 总次数实时化：按当前页出现的唯一标识编号一次聚合，覆盖落库快照值
        // （同一 unique_id 的所有记录显示同一实时值；新增/删除后刷新即变）
        applyLiveTotalCount(list, companyId);
        PageInfo<UnwarrantedMaterial> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    /** 用实时聚合值覆盖列表行的 total_count（unique_id 为 null 的行保持原值） */
    private void applyLiveTotalCount(List<UnwarrantedMaterial> list, Long companyId) {
        if (list == null || list.isEmpty()) return;
        List<String> uids = new java.util.ArrayList<>();
        for (UnwarrantedMaterial r : list) {
            if (r.getUniqueId() != null && !r.getUniqueId().isBlank()) uids.add(r.getUniqueId());
        }
        if (uids.isEmpty()) return;
        java.util.Map<String, Integer> live = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : mapper.countGroupedByUniqueIds(uids, companyId)) {
            Object uid = row.get("uid");
            Object cnt = row.get("cnt");
            if (uid != null && cnt != null) live.put(String.valueOf(uid), ((Number) cnt).intValue());
        }
        for (UnwarrantedMaterial r : list) {
            Integer v = live.get(r.getUniqueId());
            if (v != null) r.setTotalCount(v);
        }
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
        // 未过保列不再回填（独立计算，提交时由 applyCalculations 按唯一标识编号+上次维修日期判定）
        dto.setWarrantyStatus("");
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
                                                 String recordDate, Integer quantity, Long companyId,
                                                 Long originalRecordId, Long excludeId) {
        UnwarrantedMaterialComputeDTO dto = new UnwarrantedMaterialComputeDTO();
        try {
            UnwarrantedMaterial tmp = new UnwarrantedMaterial();
            tmp.setFactory(factory);
            tmp.setMachineNo(machineNo);
            tmp.setMaterialCode(materialCode);
            tmp.setQuantity(quantity);
            tmp.setOriginalRecordId(originalRecordId);
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
            dto.setRepairAmount(tmp.getRepairAmount());
        } catch (Exception ignored) {
            // 参数不合法时不返回派生值，前端保持原值
        }
        return dto;
    }

    // =============== 维修记录自动下推 ===============

    /**
     * 由维修记录自动下推一条未过保物料（新增维修记录且数量 &gt;= 1 时由 OriginalRecordService.create 调用，同事务）。
     * 基础字段按 {@link #lookupOriginal} 的映射规则回填（处理方式←维修描述、上机物料←上机物料、
     * 未过保←是否过保（"无"置空）、配件名称←零件名称、料号←物料编码）；
     * 派生字段（唯一标识编号/第几次/总次数/上次日期/超六个月/使用时长/维修金额/类别等）由
     * {@link #applyCalculations} 统一计算，查不到/算不出的字段保持为空。
     * 前置约束：调用方保证该维修记录为新建（original_record_id 尚未被关联，无需重复唯一性校验）。
     */
    @Transactional
    public UnwarrantedMaterial createFromOriginalRecord(OriginalRecord o) {
        UnwarrantedMaterial uw = new UnwarrantedMaterial();
        uw.setCompanyId(o.getCompanyId() != null ? o.getCompanyId() : 1L);
        fillFromOriginal(uw, o);
        // 派生字段统一计算
        applyCalculations(uw);
        String user = ServiceHelper.getCurrentUserName();
        uw.setCreatedBy(user);
        uw.setUpdatedBy(user);
        mapper.insert(uw);
        return uw;
    }

    /**
     * 编辑维修记录后同步已下推的未过保物料（OriginalRecordService.update 调用，同事务）。
     * 该维修记录关联的全部未过保物料：基础字段重新回填 + 派生字段重新计算 + 更新。
     * 未关联任何未过保物料时无操作（下推由 {@link #pushFromOriginalRecord} 按数量条件处理）。
     */
    @Transactional
    public void syncFromOriginalRecord(OriginalRecord o) {
        Long cid = o.getCompanyId() != null ? o.getCompanyId() : 1L;
        List<UnwarrantedMaterial> linked = mapper.findByOriginalRecordId(o.getId(), cid);
        String user = ServiceHelper.getCurrentUserName();
        for (UnwarrantedMaterial uw : linked) {
            fillFromOriginal(uw, o);
            applyCalculations(uw);
            uw.setUpdatedBy(user);
            mapper.update(uw);
        }
    }

    /**
     * 维修记录保存（新增/编辑）后的统一入口：
     * 数量 &gt;= 1 时——已有关联记录则同步更新，无关联则下推新增；
     * 数量 &lt; 1（0/空）时——删除已关联的未过保物料记录（下推条件不满足，历史遗留关联一并清理）。
     */
    @Transactional
    public void pushFromOriginalRecord(OriginalRecord o) {
        Long cid = o.getCompanyId() != null ? o.getCompanyId() : 1L;
        List<UnwarrantedMaterial> linked = mapper.findByOriginalRecordId(o.getId(), cid);
        if (o.getQuantity() == null || o.getQuantity() < 1) {
            // 数量不满足下推条件：删除已关联的记录
            if (!linked.isEmpty()) {
                mapper.deleteByOriginalRecordId(o.getId(), cid);
            }
            return;
        }
        if (!linked.isEmpty()) {
            String user = ServiceHelper.getCurrentUserName();
            for (UnwarrantedMaterial uw : linked) {
                fillFromOriginal(uw, o);
                applyCalculations(uw);
                uw.setUpdatedBy(user);
                mapper.update(uw);
            }
        } else {
            createFromOriginalRecord(o);
        }
    }

    /** 删除维修记录时级联删除其关联的未过保物料（同事务） */
    @Transactional
    public void deleteByOriginalRecordId(Long originalRecordId, Long companyId) {
        if (originalRecordId == null) return;
        Long cid = companyId != null ? companyId : 1L;
        mapper.deleteByOriginalRecordId(originalRecordId, cid);
    }

    /** 某维修记录在当前公司内已关联的未过保物料条数（前端删除提示用） */
    public int countByOriginalRecordId(Long originalRecordId, Long companyId) {
        if (originalRecordId == null) return 0;
        Long cid = companyId != null ? companyId : 1L;
        return mapper.countByOriginalRecordId(originalRecordId, cid, null);
    }

    /** 多条维修记录的关联未过保物料总数（前端批量删除提示用） */
    public int countByOriginalRecordIds(List<Long> ids, Long companyId) {
        if (ids == null || ids.isEmpty()) return 0;
        return mapper.countByOriginalRecordIds(ids, companyId);
    }

    /** 基础字段回填（映射规则与 lookupOriginal 一致） */
    private void fillFromOriginal(UnwarrantedMaterial uw, OriginalRecord o) {
        uw.setOriginalRecordId(o.getId());
        uw.setRecordDate(o.getRecordDate());
        uw.setFactory(o.getFactory());
        uw.setMachineNo(o.getMachineNo());
        uw.setEquipRepairDebugging(o.getFaultDescription());
        uw.setRepairMaterialOn(o.getMachineOnMaterial());
        uw.setRepairPerson(o.getRepairPerson());
        // 未过保列不再回填（独立计算，applyCalculations 按唯一标识编号+上次维修日期判定）
        uw.setPartName(o.getPartName());
        uw.setQuantity(o.getQuantity());
        uw.setMaterialCode(o.getMaterialCode());
    }

    // =============== Excel 导入 ===============

    /**
     * 批量导入 Excel 数据。
     * 两阶段处理：先读取全部行（保留 Excel 行序），再按唯一标识编号分组、组内按（日期, 行序）排序后
     * 逐条计算派生字段，最后分批插入。
     * 一次性导入时同组记录互不可见，若逐条查库计算会出现同组同日多条"第几次/总次数"相同的错误；
     * 原表行序即业务顺序（已全量验证），组内前序条数/上一条记录作为计算上下文，保证导入结果与原表一致。
     */
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<UnwarrantedMaterial> all = new ArrayList<>();
        int[] counts = {0, 0, 0}; // total, success, fail

        // 阶段一：读取全部行（行序 = Excel 行序）
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
                        // 未过保列由阶段二 applyCalculations 独立计算，此处不回填
                        all.add(data);
                    } catch (Exception e) {
                        failDetails.add(new ImportResultDTO.FailDetail(counts[0], e.getMessage()));
                        counts[2]++;
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext ctx) {
                    // 两阶段导入：解析阶段不写库，全部行读完后统一分组计算+分批插入
                }
            }).sheet().doRead();
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage());
        }

        // 阶段二：同唯一标识编号分组，组内按 Excel 行序（读取顺序）逐条计算派生字段。
        // 注意：不按日期重排——原表行序即业务顺序（已全量验证，少量组内日期乱序但第几次仍按行序编号），
        // 组内前序条数/上一条记录作为计算上下文，保证导入结果与原表一致。
        Map<String, List<UnwarrantedMaterial>> groups = new LinkedHashMap<>();
        for (UnwarrantedMaterial r : all) {
            if (hasGroupKey(r)) {
                groups.computeIfAbsent(groupKey(r), k -> new ArrayList<>()).add(r);
            } else {
                // 无有效分组键的行：保持原有单条计算逻辑
                applyCalculations(r);
            }
        }
        for (List<UnwarrantedMaterial> g : groups.values()) {
            UnwarrantedMaterial prev = null;
            int extra = 0; // 组内排在本条之前的条数
            for (UnwarrantedMaterial r : g) {
                applyCalculations(r, extra, g.size(), prev);
                prev = r;
                extra++;
            }
        }

        // 阶段三：分批插入
        List<UnwarrantedMaterial> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        for (UnwarrantedMaterial r : all) {
            batch.add(r);
            if (batch.size() >= IMPORT_BATCH_SIZE) {
                flushBatch(batch, counts);
            }
        }
        if (!batch.isEmpty()) {
            flushBatch(batch, counts);
        }

        ImportResultDTO result = new ImportResultDTO();
        result.setTotal(counts[0]);
        result.setSuccess(counts[1]);
        result.setFail(counts[2]);
        result.setFailDetails(failDetails);
        return result;
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
            // 总次数实时化：导出全量用全量 GROUP BY 聚合覆盖（避免 IN 参数超限）
            if (companyId != null) {
                java.util.Map<String, Integer> live = new java.util.HashMap<>();
                for (java.util.Map<String, Object> row : mapper.countGroupedByUniqueIdAll(companyId)) {
                    Object uid = row.get("uid");
                    Object cnt = row.get("cnt");
                    if (uid != null && cnt != null) live.put(String.valueOf(uid), ((Number) cnt).intValue());
                }
                for (UnwarrantedMaterial r : list) {
                    Integer v = live.get(r.getUniqueId());
                    if (v != null) r.setTotalCount(v);
                }
            }

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
            template.setEquipRepairDebugging("示例处理方式");
            template.setRepairMaterialOn("示例上机物料");
            template.setRepairPerson("示例维修人");
            template.setWarrantyStatus("未过保");
            template.setPartName("示例配件名称");
            template.setQuantity(1);
            template.setMaterialCode("示例料号");
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

    /** 按料号从 156 项表回填配件名称（已有值不覆盖；查不到保留原值）——分组键/唯一标识编号依赖配件名称 */
    private void fillPartName(UnwarrantedMaterial r) {
        if (!notBlank(r.getMaterialCode()) || notBlank(r.getPartName())) return;
        try {
            com.metal.entity.BaseMaterial156 item = baseMaterial156Mapper.findByMaterialCode(r.getMaterialCode());
            if (item != null && notBlank(item.getPartName())) {
                r.setPartName(item.getPartName());
            }
        } catch (Exception ignored) {
            // 查询失败不阻塞
        }
    }

    /**
     * 派生字段统一计算（单条场景：新增/编辑/预览）。覆盖前端传入值，保证一致性（参照 DeliveryStatsService.applyCalculations）。
     * warrantyStatus 由 {@link #applyCalculations(UnwarrantedMaterial, int, Integer, UnwarrantedMaterial)} 独立计算；
     * repairAmount 按超比统计表含税单价 × 数量自动计算覆盖。
     */
    private void applyCalculations(UnwarrantedMaterial r) {
        applyCalculations(r, 0, null, null);
    }

    /**
     * 派生字段统一计算（批量导入场景带组上下文）。
     * 一次性导入时同组（同唯一标识编号）记录尚未入库、互不可见，需要以组内前序条数/上一条记录补充顺序信息：
     * 第几次 = 库中已有（≤ 当天）条数 + 组内前序条数 + 1；总次数 = 库中已有（全部日期）+ 本组条数；
     * 上次日期/上次维修人优先取组内上一条记录（同日多条也正确）。
     * 未过保列也在此独立计算（距上次维修 < 6 个月 → 未过保；首次/≥6 个月 → 空），覆盖前端传入值。
     *
     * @param groupExtra  组内排在本条之前的记录条数（非导入场景传 0）
     * @param groupSize   本组记录总条数（非导入场景传 null，此时总次数 = 库中已有 + 1）
     * @param prevInGroup 组内上一条记录（非导入场景传 null，此时查库取最近一条）
     */
    private void applyCalculations(UnwarrantedMaterial r, int groupExtra, Integer groupSize, UnwarrantedMaterial prevInGroup) {
        fillCategory(r);
        fillPartName(r);
        LocalDate date = r.getRecordDate();
        if (date != null) {
            r.setCurrentDate(date);
            r.setYearMonth(date.format(YM_FMT));
        } else {
            r.setCurrentDate(null);
            r.setYearMonth(null);
        }

        // 维修金额（合约）= 超比统计表含税单价 × 数量：按当前日期月份（yyyy-MM）+ 料号匹配超比统计表，查不到单价则置空
        if (date != null && r.getCompanyId() != null && notBlank(r.getMaterialCode()) && r.getQuantity() != null) {
            String ym = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            BigDecimal price = deliveryStatsMapper.findUnitPriceByMonthAndMaterial(r.getCompanyId(), ym, r.getMaterialCode());
            r.setRepairAmount(price != null ? price.multiply(BigDecimal.valueOf(r.getQuantity())) : null);
        } else {
            r.setRepairAmount(null);
        }

        // 唯一标识编号的配件名称：优先取关联维修记录（original_record）的配件名称（用户口径）；
        // 无关联时（手动新增/导入）用本条记录自身配件名称（料号回填或手工填写）
        String uniquePartName = r.getPartName();
        if (r.getOriginalRecordId() != null && r.getCompanyId() != null) {
            try {
                OriginalRecord or = originalRecordMapper.findByIdAndCompany(r.getOriginalRecordId(), r.getCompanyId());
                if (or != null && notBlank(or.getPartName())) uniquePartName = or.getPartName();
            } catch (Exception ignored) {
                // 维修记录查询失败不阻塞
            }
        }
        boolean hasKey = notBlank(r.getFactory()) && notBlank(r.getMachineNo()) && notBlank(uniquePartName);
        String uniqueId = null;
        if (hasKey) {
            // 唯一标识编号：厂房-机台号配件名称（机台号与配件名称之间无分隔符，如 F6-A15主轴）
            uniqueId = r.getFactory() + "-" + r.getMachineNo() + uniquePartName;
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
            r.setWarrantyStatus(""); // 无唯一标识编号/日期时无法判断是否未过保，置空
            r.setOverSixMonths(null);
            r.setUsageMonths(null);
            r.setLastRepairPerson(null);
            return;
        }

        Long cid = r.getCompanyId();
        Long selfId = r.getId(); // create/导入时为 null（同日已有记录视为更早，追加语义）；update 时为当前 id（同日按 id 稳定排序，互不干扰）
        // 总次数：同一唯一标识编号出现的总次数 + 1（与日期无关）；批量导入时 + 本组全部条数
        r.setTotalCount(mapper.countByUniqueId(uniqueId, cid, selfId) + (groupSize != null ? groupSize : 1));
        // 第几次：截至当前日期的出现次数 + 组内前序条数 + 1，不计当前日期之后的记录
        r.setOccurrenceNo(mapper.countByUniqueIdBefore(uniqueId, cid, date, selfId) + groupExtra + 1);

        // 上次日期/上次维修人：批量导入时组内上一条记录优先；否则取库中距离当前日期最近一次（<=当前日期）的出现记录
        UnwarrantedMaterial prev = prevInGroup != null ? prevInGroup : mapper.findLatestByUniqueIdBefore(uniqueId, cid, date, selfId);
        if (prev != null) {
            r.setLastDate(prev.getRecordDate());
            r.setLastRepairPerson(prev.getRepairPerson());
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

        // 未过保列独立计算（用户口径，2026-08）：按唯一标识编号查最近一次维修，
        // 距本次 < 6 个月 → "未过保"；>= 6 个月或首次维修（无上次记录）→ 空。
        // 覆盖前端传入/回填值，保证一致性；不再依赖维修记录"是否过保"字段。
        if (r.getLastDate() != null && date != null
                && ChronoUnit.MONTHS.between(r.getLastDate(), date) < 6) {
            r.setWarrantyStatus("未过保");
        } else {
            r.setWarrantyStatus("");
        }
    }

    /** 是否有有效分组键（厂房/机台号/配件名称齐全）——与唯一标识编号的计算条件一致 */
    private boolean hasGroupKey(UnwarrantedMaterial r) {
        return notBlank(r.getFactory()) && notBlank(r.getMachineNo()) && notBlank(r.getPartName());
    }

    /** 导入分组的组键：公司 + 唯一标识编号（厂房-机台号配件名称，与 applyCalculations 生成规则一致） */
    private String groupKey(UnwarrantedMaterial r) {
        return (r.getCompanyId() != null ? r.getCompanyId() : 1L) + "|" + r.getFactory() + "-" + r.getMachineNo() + r.getPartName();
    }
}
