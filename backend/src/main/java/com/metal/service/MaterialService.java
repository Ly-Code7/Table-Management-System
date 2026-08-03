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
import com.metal.entity.Material;
import com.metal.mapper.MaterialMapper;
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
public class MaterialService {

    @Autowired
    private MaterialMapper mapper;

    public PageResult<Material> query(int page, int pageSize, Long companyId, String keyword) {
        PageHelper.startPage(page, pageSize);
        List<Material> list = mapper.search(companyId, keyword, "id", "desc");
        PageInfo<Material> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public Material getById(Long id) {
        Material m = mapper.findById(id);
        if (m == null) throw new BizException("物料不存在");
        return m;
    }

    @Transactional
    public Material create(Material material) {
        // 公司默认值：先确定 companyId，保证备注唯一校验在公司内生效，而不是全局唯一
        if (material.getCompanyId() == null) material.setCompanyId(1L);
        // 备注唯一校验：同一公司内备注值不允许重复（备注为空时不校验）
        if (material.getRemark() != null && !material.getRemark().isBlank()) {
            Material dup = mapper.findByRemark(material.getRemark(), material.getCompanyId());
            if (dup != null) {
                throw new BizException("备注【" + material.getRemark() + "】已存在，不允许重复添加");
            }
        }
        String user = ServiceHelper.getCurrentUserName();
        material.setCreatedBy(user);
        material.setUpdatedBy(user);
        mapper.insert(material);
        return material;
    }

    @Transactional
    public Material update(Material material) {
        Material exist = getById(material.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        material.setUpdatedBy(ServiceHelper.getCurrentUserName());
        mapper.update(material);
        return material;
    }

    @Transactional
    public void delete(Long id) {
        Material exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        mapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        if (!ServiceHelper.isAdmin()) {
            for (Long id : ids) {
                Material exist = getById(id);
                ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            }
        }
        mapper.batchDelete(ids);
    }

    public List<Material> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return mapper.searchByKeyword(keyword);
    }

    // =============== Excel 导入 ===============
    private static final int IMPORT_BATCH_SIZE = 500;

    /**
     * 批量导入 Excel 数据
     */
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<Material> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0}; // total, success, fail

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, Material.class, new AnalysisEventListener<Material>() {
                @Override
                public void invoke(Material data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        if (data.getMaterialCode() == null || data.getMaterialCode().isBlank()) {
                            failDetails.add(new ImportResultDTO.FailDetail(counts[0], "物料编码不能为空"));
                            counts[2]++;
                            return;
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

    /** 将缓冲区数据批量写入数据库 */
    private void flushBatch(List<Material> batch, int[] counts) {
        mapper.batchInsert(batch);
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword) {
        try {
            PageHelper.startPage(1, 0); // 0 disables paging
            List<Material> list = mapper.search(companyId, keyword, "id", "desc");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("物料导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, Material.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("物料")
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
            String fileName = URLEncoder.encode("物料导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            Material template = new Material();
            template.setCategory("示例类别");
            template.setMaterialName("示例物料");
            template.setSpecModel("示例规格");
            template.setMaterialCode("示例编码");

            List<Material> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, Material.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("物料")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }
}
