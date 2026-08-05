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
import com.metal.entity.MachineCount;
import com.metal.mapper.MachineCountMapper;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class MachineCountService {

    @Autowired
    private MachineCountMapper mapper;

    public PageResult<MachineCount> query(int page, int pageSize, Long companyId, String keyword,
                                           String statMonth, String sortField, String sortOrder) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<MachineCount> list = mapper.search(companyId, keyword, statMonth, sortField, sortOrder);
        PageInfo<MachineCount> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public List<MachineCount> findByMonth(String statMonth, Long companyId) {
        return mapper.findByMonth(statMonth, companyId);
    }

    @Transactional
    public int clearByMonth(String statMonth, Long companyId) {
        if (statMonth == null || statMonth.isBlank()) throw new BizException("月份不能为空");
        Long cid = companyId != null ? companyId : 1L;
        return mapper.deleteByMonthExceptBaseline(statMonth, cid);
    }

    public MachineCount getById(Long id) {
        MachineCount r = mapper.findById(id);
        if (r == null) throw new BizException("记录不存在");
        return r;
    }

    @Transactional
    public MachineCount create(MachineCount record) {
        if (record.getStatMonth() == null || record.getStatMonth().isBlank()) {
            throw new BizException("统计月份不能为空");
        }
        // 公司兜底：未传时默认归属公司 1（与其他模块一致），防止 company_id NULL 入库
        if (record.getCompanyId() == null) record.setCompanyId(1L);
        // 前端勾选了基准线，设置占比为100
        if (Boolean.TRUE.equals(record.getIsBaseline())) {
            record.setRatioPct(new BigDecimal("100.00"));
        }
        String user = ServiceHelper.getCurrentUserName();
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        applyRatioCalculation(record, null);
        mapper.insert(record);
        // 如果当前记录是基准线，重算同月其他所有记录（公司内）
        if (isBaseline(record)) {
            recalculateAllInMonth(record.getStatMonth(), record.getId(), record.getCompanyId());
        }
        return record;
    }

    @Transactional
    public MachineCount update(MachineCount record) {
        MachineCount exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        // 公司兜底：沿用原记录公司，防止 company_id NULL 入库
        if (record.getCompanyId() == null) record.setCompanyId(exist.getCompanyId() != null ? exist.getCompanyId() : 1L);
        // 前端勾选了基准线，设置占比为100
        if (Boolean.TRUE.equals(record.getIsBaseline())) {
            record.setRatioPct(new BigDecimal("100.00"));
        }
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        applyRatioCalculation(record, exist.getId());
        mapper.update(record);
        // 重算同月所有记录（排除自身，因为自身已更新；公司内）
        recalculateAllInMonth(record.getStatMonth(), record.getId(), record.getCompanyId());
        return record;
    }

    @Transactional
    public void delete(Long id) {
        MachineCount exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        String statMonth = exist.getStatMonth();
        Long companyId = exist.getCompanyId() != null ? exist.getCompanyId() : 1L;

        // 检查是否删除的是基准线（公司内）
        if (isBaseline(exist)) {
            List<MachineCount> others = mapper.findByMonth(statMonth, companyId);
            // 排除自身
            others.removeIf(r -> r.getId().equals(id));
            if (!others.isEmpty()) {
                throw new BizException("该记录为当月基准线（开机总数），请先将其他记录设为基准线后再删除，或删除该月所有记录");
            }
        }

        mapper.deleteById(id);

        // 删除后如果还有其他记录但没有基准线，选数量最大的作为新基准线（公司内）
        List<MachineCount> remaining = mapper.findByMonth(statMonth, companyId);
        if (!remaining.isEmpty()) {
            boolean hasBaseline = remaining.stream().anyMatch(this::isBaseline);
            if (!hasBaseline) {
                // 选数量最大的作为新基准线
                MachineCount newBaseline = remaining.get(0); // 已按 count DESC 排序
                newBaseline.setRatioPct(new BigDecimal("100.00"));
                mapper.update(newBaseline);
                recalculateAllInMonth(statMonth, newBaseline.getId(), companyId);
            }
        }
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        if (!ServiceHelper.isAdmin()) {
            for (Long id : ids) {
                MachineCount exist = getById(id);
                ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            }
        }
        // 逐条删除以触发基准线保护逻辑
        for (Long id : ids) {
            delete(id);
        }
    }

    // =============== 占比自动计算 ===============

    /** 判断记录是否为基准线（占比100%） */
    private boolean isBaseline(MachineCount record) {
        return record.getRatioPct() != null && record.getRatioPct().compareTo(new BigDecimal("100.00")) == 0;
    }

    /**
     * 为单条记录计算占比
     * @param record    当前记录
     * @param excludeId 编辑时排除自身ID，新增时传 null
     */
    private void applyRatioCalculation(MachineCount record, Long excludeId) {
        if (record.getCount() == null) {
            record.setCount(0);
        }

        if (isBaseline(record)) {
            // 当前记录设为基准线：需要把同月旧基准线降级（公司内）
            MachineCount oldBaseline = mapper.findBaselineByMonth(record.getStatMonth(), record.getCompanyId());
            if (oldBaseline != null && !oldBaseline.getId().equals(excludeId)) {
                // 旧基准线按新基准线重算占比
                if (record.getCount() > 0) {
                    BigDecimal newRatio = BigDecimal.valueOf(oldBaseline.getCount())
                            .divide(BigDecimal.valueOf(record.getCount()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                    oldBaseline.setRatioPct(newRatio);
                    mapper.update(oldBaseline);
                }
            }
        } else {
            // 非基准线：根据当月基准线计算占比（公司内）
            MachineCount baseline = mapper.findBaselineByMonth(record.getStatMonth(), record.getCompanyId());
            if (baseline != null && !baseline.getId().equals(excludeId) && baseline.getCount() > 0) {
                BigDecimal ratio = BigDecimal.valueOf(record.getCount())
                        .divide(BigDecimal.valueOf(baseline.getCount()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                record.setRatioPct(ratio);
            } else {
                // 当月无基准线，第一条记录自动成为基准线
                record.setRatioPct(new BigDecimal("100.00"));
            }
        }
    }

    /**
     * 重算当月所有记录的占比（基于当前基准线，公司内）
     * @param statMonth        统计月份
     * @param excludeBaselineId 排除的基准线ID（该基准线已更新，不需要重算自己）
     * @param companyId        公司ID
     */
    private void recalculateAllInMonth(String statMonth, Long excludeBaselineId, Long companyId) {
        Long cid = companyId != null ? companyId : 1L;
        MachineCount baseline = mapper.findBaselineByMonth(statMonth, cid);
        if (baseline == null) return;

        List<MachineCount> all = mapper.findByMonth(statMonth, cid);
        List<MachineCount> toUpdate = new ArrayList<>();

        for (MachineCount r : all) {
            if (r.getId().equals(excludeBaselineId)) continue;
            if (isBaseline(r) && !r.getId().equals(baseline.getId())) {
                // 旧的基准线标记（不应该出现，但做个防御）
                if (baseline.getCount() > 0) {
                    BigDecimal ratio = BigDecimal.valueOf(r.getCount())
                            .divide(BigDecimal.valueOf(baseline.getCount()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                    r.setRatioPct(ratio);
                    toUpdate.add(r);
                }
            } else if (!isBaseline(r)) {
                if (baseline.getCount() > 0) {
                    BigDecimal ratio = BigDecimal.valueOf(r.getCount())
                            .divide(BigDecimal.valueOf(baseline.getCount()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                    r.setRatioPct(ratio);
                    toUpdate.add(r);
                }
            }
        }

        if (!toUpdate.isEmpty()) {
            mapper.batchUpdateRatio(toUpdate);
        }
    }

    // =============== Excel 导入 ===============
    private static final int IMPORT_BATCH_SIZE = 500;

    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<MachineCount> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0}; // total, success, fail

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, MachineCount.class, new AnalysisEventListener<MachineCount>() {
                @Override
                public void invoke(MachineCount data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        String user = ServiceHelper.getCurrentUserName();
                        data.setCompanyId(companyId != null ? companyId : 1L);
                        data.setCreatedBy(user);
                        data.setUpdatedBy(user);
                        // Handle ratioPct: if value is 0~1 range, multiply by 100
                        if (data.getRatioPct() != null) {
                            BigDecimal r = data.getRatioPct();
                            if (r.compareTo(BigDecimal.ONE) <= 0) {
                                data.setRatioPct(r.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
                            }
                        }
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

    private void flushBatch(List<MachineCount> batch, int[] counts) {
        mapper.batchInsert(batch);
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword, String statMonth) {
        try {
            PageHelper.startPage(1, 0); // 0 disables paging
            List<MachineCount> list = mapper.search(companyId, keyword, statMonth, "id", "desc");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("机型统计导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, MachineCount.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("机型统计")
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
            String fileName = URLEncoder.encode("机型统计导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            MachineCount template = new MachineCount();
            template.setMachineModel("示例机型");
            template.setCount(1);
            template.setRatioPct(new BigDecimal("100.00"));
            template.setStatMonth("2025-01");
            template.setRemark("示例备注");

            List<MachineCount> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, MachineCount.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("机型统计")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }
}
