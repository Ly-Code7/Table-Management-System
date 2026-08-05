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
import com.metal.entity.BaseMaterial156;
import com.metal.mapper.BaseMaterial156Mapper;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class BaseMaterial156Service {

    @Autowired
    private BaseMaterial156Mapper mapper;

    public PageResult<BaseMaterial156> query(int page, int pageSize, Long companyId, String keyword,
                                              String sortField, String sortOrder) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<BaseMaterial156> list = mapper.search(companyId, keyword, sortField, sortOrder);
        PageInfo<BaseMaterial156> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public BaseMaterial156 getById(Long id) {
        BaseMaterial156 r = mapper.findById(id);
        if (r == null) throw new BizException("记录不存在");
        return r;
    }

    public List<BaseMaterial156> searchByKeyword(String keyword, Long companyId) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return mapper.searchByKeyword(keyword, companyId);
    }

    public BaseMaterial156 findByMaterialCode(String materialCode) {
        return mapper.findByMaterialCode(materialCode);
    }

    @Transactional
    public BaseMaterial156 create(BaseMaterial156 record) {
        // 料号唯一性校验（含公司ID）
        if (record.getMaterialCode() != null && !record.getMaterialCode().isBlank()) {
            Long cid = record.getCompanyId() != null ? record.getCompanyId() : 1L;
            if (mapper.countByMaterialCode(record.getMaterialCode(), cid) > 0) {
                throw new BizException("料号 '" + record.getMaterialCode() + "' 在当前公司已存在");
            }
        }
        String user = ServiceHelper.getCurrentUserName();
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        mapper.insert(record);
        return record;
    }

    @Transactional
    public BaseMaterial156 update(BaseMaterial156 record) {
        BaseMaterial156 exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        // 料号唯一性校验（排除自身，含公司ID）
        if (record.getMaterialCode() != null && !record.getMaterialCode().isBlank()
                && !record.getMaterialCode().equals(exist.getMaterialCode())) {
            Long cid = record.getCompanyId() != null ? record.getCompanyId() : exist.getCompanyId() != null ? exist.getCompanyId() : 1L;
            if (mapper.countByMaterialCode(record.getMaterialCode(), cid) > 0) {
                throw new BizException("料号 '" + record.getMaterialCode() + "' 在当前公司已存在");
            }
        }
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        mapper.update(record);
        return record;
    }

    @Transactional
    public void delete(Long id) {
        BaseMaterial156 exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        mapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        if (!ServiceHelper.isAdmin()) {
            for (Long id : ids) {
                BaseMaterial156 exist = getById(id);
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
        List<BaseMaterial156> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0};

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, BaseMaterial156.class, new AnalysisEventListener<BaseMaterial156>() {
                @Override
                public void invoke(BaseMaterial156 data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        if (data.getMaterialCode() == null || data.getMaterialCode().isBlank()) {
                            failDetails.add(new ImportResultDTO.FailDetail(counts[0], "料号不能为空"));
                            counts[2]++;
                            return;
                        }
                        if (mapper.countByMaterialCode(data.getMaterialCode(), companyId != null ? companyId : 1L) > 0) {
                            failDetails.add(new ImportResultDTO.FailDetail(counts[0],
                                    "料号 '" + data.getMaterialCode() + "' 已存在"));
                            counts[2]++;
                            return;
                        }
                        String user = ServiceHelper.getCurrentUserName();
                        data.setCompanyId(companyId != null ? companyId : 1L);
                        data.setCreatedBy(user);
                        data.setUpdatedBy(user);
                        // Handle percentage: if ratio is 0~1 range keep as-is, if > 1 divide by 100
                        if (data.getRatio() != null) {
                            java.math.BigDecimal r = data.getRatio();
                            if (r.compareTo(java.math.BigDecimal.ONE) > 0) {
                                data.setRatio(r.divide(java.math.BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
                            }
                        }
                        batch.add(data);
                        if (batch.size() >= IMPORT_BATCH_SIZE) flushBatch(batch, counts);
                    } catch (Exception e) {
                        failDetails.add(new ImportResultDTO.FailDetail(counts[0], e.getMessage()));
                        counts[2]++;
                    }
                }
                @Override
                public void doAfterAllAnalysed(AnalysisContext ctx) {
                    if (!batch.isEmpty()) {
                        try {
                            flushBatch(batch, counts);
                        } catch (Exception e) {
                            failDetails.add(new ImportResultDTO.FailDetail(counts[0], "批量写入失败: " + e.getMessage()));
                            counts[2] += batch.size();
                            batch.clear();
                        }
                    }
                }
                @Override
                public void onException(Exception e, AnalysisContext ctx) {
                    counts[0]++;
                    int row = ctx.readRowHolder() != null ? ctx.readRowHolder().getRowIndex() + 1 : counts[0];
                    failDetails.add(new ImportResultDTO.FailDetail(row, "第" + row + "行解析失败: " + e.getMessage()));
                    counts[2]++;
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

    private void flushBatch(List<BaseMaterial156> batch, int[] counts) {
        mapper.batchInsert(batch);
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword) {
        try {
            PageHelper.startPage(1, 0);
            List<BaseMaterial156> list = mapper.search(companyId, keyword, "id", "desc");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("156项导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, BaseMaterial156.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("156项")
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
            String fileName = URLEncoder.encode("156项导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            // 只写表头，不写示例数据行
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, BaseMaterial156.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("156项")
                    .doWrite(List.of());
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }
}
