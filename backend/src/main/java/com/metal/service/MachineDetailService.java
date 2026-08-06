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
import com.metal.entity.MachineDetail;
import com.metal.mapper.MachineDetailMapper;
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
public class MachineDetailService {

    @Autowired
    private MachineDetailMapper mapper;

    @Autowired
    private OperationLogService logService;

    public PageResult<MachineDetail> query(int page, int pageSize, Long companyId, String keyword,
                                            String factory, String brand,
                                            String sortField, String sortOrder) {
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);
        PageHelper.startPage(page, pageSize);
        List<MachineDetail> list = mapper.search(companyId, keyword, factory, brand, sortField, sortOrder);
        PageInfo<MachineDetail> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public MachineDetail getById(Long id) {
        MachineDetail r = mapper.findById(id);
        if (r == null) throw new BizException("记录不存在");
        return r;
    }

    @Transactional
    public MachineDetail create(MachineDetail record) {
        // 公司兜底：未传时默认归属公司 1（与其他模块一致），防止 company_id NULL 入库
        if (record.getCompanyId() == null) record.setCompanyId(1L);
        String user = ServiceHelper.getCurrentUserName();
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        applyPlantMachine(record);
        checkPlantMachineUnique(record.getPlantMachine(), record.getCompanyId(), null);
        mapper.insert(record);
        logService.log("INSERT", "machine_detail", record.getId(), record.getCompanyId(), record.toString());
        return record;
    }

    @Transactional
    public MachineDetail update(MachineDetail record) {
        MachineDetail exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        applyPlantMachine(record);
        // 仅当 厂房+机台 值发生变化时才做唯一性校验（排除自身）
        if (record.getPlantMachine() != null && !record.getPlantMachine().equals(exist.getPlantMachine())) {
            checkPlantMachineUnique(record.getPlantMachine(), record.getCompanyId(), record.getId());
        }
        mapper.update(record);
        logService.log("UPDATE", "machine_detail", record.getId(), record.getCompanyId(), record.toString());
        return record;
    }

    /** 自动拼接 厂房+机台：factory-machineNo，任一缺失则置空 */
    private void applyPlantMachine(MachineDetail record) {
        record.setPlantMachine(ServiceHelper.combineFactoryMachine(record.getFactory(), record.getMachineNo()));
    }

    /** 厂房+机台 唯一性校验（同一公司内） */
    private void checkPlantMachineUnique(String plantMachine, Long companyId, Long excludeId) {
        if (plantMachine == null || plantMachine.isBlank()) return;
        Long cid = companyId != null ? companyId : 1L;
        if (mapper.countByPlantMachine(plantMachine, cid, excludeId) > 0) {
            throw new BizException("该厂房+机台 '" + plantMachine + "' 已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        MachineDetail exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        mapper.deleteById(id);
        logService.log("DELETE", "machine_detail", id, exist.getCompanyId(), null);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        List<MachineDetail> exists = new ArrayList<>(ids.size());
        for (Long id : ids) {
            MachineDetail exist = getById(id);
            ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            exists.add(exist);
        }
        mapper.batchDelete(ids);
        for (MachineDetail exist : exists) {
            logService.log("DELETE", "machine_detail", exist.getId(), exist.getCompanyId(), null);
        }
    }

    // =============== Excel 导入 ===============
    private static final int IMPORT_BATCH_SIZE = 500;

    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<MachineDetail> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0};

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, MachineDetail.class, new AnalysisEventListener<MachineDetail>() {
                @Override
                public void invoke(MachineDetail data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        String user = ServiceHelper.getCurrentUserName();
                        data.setCompanyId(companyId != null ? companyId : 1L);
                        data.setCreatedBy(user);
                        data.setUpdatedBy(user);
                        applyPlantMachine(data);
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

    private void flushBatch(List<MachineDetail> batch, int[] counts) {
        mapper.batchInsert(batch);
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword, String factory, String brand) {
        try {
            PageHelper.startPage(1, 0);
            List<MachineDetail> list = mapper.search(companyId, keyword, factory, brand, "id", "desc");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("机台明细导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, MachineDetail.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("机台明细")
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
            String fileName = URLEncoder.encode("机台明细导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            MachineDetail template = new MachineDetail();
            template.setFactory("示例厂房");
            template.setMachineNo("示例机台号");
            template.setMachineBrand("示例机台品牌");

            List<MachineDetail> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, MachineDetail.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("机台明细")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }
}
