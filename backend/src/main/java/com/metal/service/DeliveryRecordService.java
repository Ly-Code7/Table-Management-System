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
import com.metal.entity.DeliveryRecord;
import com.metal.mapper.DeliveryRecordMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DeliveryRecordService {

    @Autowired
    private DeliveryRecordMapper mapper;

    @Autowired
    private OperationLogService logService;

    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("'FY'yyMM");

    public PageResult<DeliveryRecord> query(int page, int pageSize, Long companyId, String keyword,
                                             String category, String productAttr,
                                             String factory, String startDate, String endDate,
                                             String sortField, String sortOrder) {
        // 安全过滤排序字段，防止 SQL 注入
        sortField = ServiceHelper.sanitizeSortField(sortField, "id");
        sortOrder = ServiceHelper.sanitizeSortOrder(sortOrder);

        PageHelper.startPage(page, pageSize);
        List<DeliveryRecord> list = mapper.search(companyId, keyword, category, productAttr, factory,
                startDate, endDate, sortField, sortOrder);
        PageInfo<DeliveryRecord> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), page, pageSize, list);
    }

    public DeliveryRecord getById(Long id) {
        DeliveryRecord record = mapper.findById(id);
        if (record == null) throw new BizException("记录不存在");
        return record;
    }

    @Transactional
    public DeliveryRecord create(DeliveryRecord record) {
        applyYearMonth(record);
        String user = ServiceHelper.getCurrentUserName();
        // 使用前端传来的 companyId，未传时默认归属金属厂
        if (record.getCompanyId() == null) {
            record.setCompanyId(1L);
        }
        record.setCreatedBy(user);
        record.setUpdatedBy(user);
        mapper.insert(record);
        logService.log("INSERT", "delivery_record", record.getId(), record.getCompanyId(), record.toString());
        return record;
    }

    @Transactional
    public DeliveryRecord update(DeliveryRecord record) {
        DeliveryRecord exist = getById(record.getId());
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "编辑");
        applyYearMonth(record);
        record.setUpdatedBy(ServiceHelper.getCurrentUserName());
        mapper.update(record);
        logService.log("UPDATE", "delivery_record", record.getId(), record.getCompanyId(), record.toString());
        return record;
    }

    @Transactional
    public void delete(Long id) {
        DeliveryRecord exist = getById(id);
        ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
        mapper.deleteById(id);
        logService.log("DELETE", "delivery_record", id, exist.getCompanyId(), null);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的记录");
        List<DeliveryRecord> exists = new ArrayList<>(ids.size());
        for (Long id : ids) {
            DeliveryRecord exist = getById(id);
            ServiceHelper.checkOwnershipOrAdmin(exist.getCreatedBy(), "删除");
            exists.add(exist);
        }
        mapper.batchDelete(ids);
        for (DeliveryRecord exist : exists) {
            logService.log("DELETE", "delivery_record", exist.getId(), exist.getCompanyId(), null);
        }
    }

    public DeliveryRecord copy(Long id) {
        return getById(id);
    }

    /** 根据物料编码查询最近一条送货记录，用于新增时自动回填（公司内） */
    public DeliveryRecord getLatestByMaterialCode(String materialCode, Long companyId) {
        if (materialCode == null || materialCode.isBlank()) return null;
        return mapper.findLatestByMaterialCode(materialCode, companyId);
    }

    /** 根据物料序列号查询送货记录，用于维修记录回填料号（公司内） */
    public DeliveryRecord getByMaterialSerial(String materialSerial, Long companyId) {
        if (materialSerial == null || materialSerial.isBlank()) return null;
        return mapper.findByMaterialSerial(materialSerial, companyId);
    }

    /** 模糊匹配送货记录（料号/序列号/物料名称 LIKE），公司内取最近一条，用于维修记录"下机料号"自动回填；无匹配返回 null */
    public DeliveryRecord getFuzzyByKeyword(String keyword, Long companyId) {
        if (keyword == null || keyword.isBlank()) return null;
        return mapper.findFuzzyByKeyword(keyword.trim(), companyId);
    }

    // =============== Excel 导入 ===============
    private static final int IMPORT_BATCH_SIZE = 500; // 每批 500 条，平衡内存与数据库往返

    /**
     * 批量导入 Excel 数据
     * 采用分批 INSERT + 事务保护：每 500 条一批，中途失败自动回滚整批
     */
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file, Long companyId) {
        List<ImportResultDTO.FailDetail> failDetails = new ArrayList<>();
        List<DeliveryRecord> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
        int[] counts = {0, 0, 0}; // total, success, fail

        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is, DeliveryRecord.class, new AnalysisEventListener<DeliveryRecord>() {
                @Override
                public void invoke(DeliveryRecord data, AnalysisContext ctx) {
                    counts[0]++;
                    try {
                        applyYearMonth(data);
                        String user = ServiceHelper.getCurrentUserName();
                        // 使用导入时指定的公司ID，未传时默认归属金属厂
                        data.setCompanyId(companyId != null ? companyId : 1L);
                        data.setCreatedBy(user);
                        data.setUpdatedBy(user);
                        batch.add(data);

                        // 攒够一批就写入数据库
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
                    // 最后一批不足 500 条的剩余数据
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

    /** 将缓冲区数据批量写入数据库（导入不写操作日志——用户决策，避免上万条导入放大日志开销） */
    private void flushBatch(List<DeliveryRecord> batch, int[] counts) {
        mapper.batchInsert(batch);
        counts[1] += batch.size();
        batch.clear();
    }

    // =============== Excel 导出 ===============
    public void exportExcel(HttpServletResponse response, Long companyId, String keyword, String category,
                            String productAttr, String factory, String startDate, String endDate) {
        try {
            // 不分页，全量查询
            PageHelper.startPage(1, 0); // 0 disables paging
            List<DeliveryRecord> list = mapper.search(companyId, keyword, category, productAttr, factory,
                    startDate, endDate, "id", "desc");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("送货记录导出.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, DeliveryRecord.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("送货记录")
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
            String fileName = URLEncoder.encode("送货记录导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            // 创建一个只有表头的空模板
            DeliveryRecord template = new DeliveryRecord();
            template.setRecordDate(LocalDate.now());
            template.setCategory("示例类别");
            template.setMaterialName("示例物料");
            template.setSpecModel("示例规格");
            template.setMaterialCode("示例编码");
            template.setMaterialSerial("示例序列号");
            template.setQuantity(1);
            template.setUnit("个");
            template.setBrand("示例品牌");
            template.setProductAttr("新品");
            template.setFactory("示例厂房");
            template.setShipmentNo("示例单号");

            List<DeliveryRecord> list = List.of(template);
            OutputStream os = response.getOutputStream();
            EasyExcel.write(os, DeliveryRecord.class)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("送货记录")
                    .doWrite(list);
            os.flush();
        } catch (IOException e) {
            throw new BizException("模板下载失败: " + e.getMessage());
        }
    }

    // =============== 辅助方法 ===============
    private void applyYearMonth(DeliveryRecord record) {
        if (record.getRecordDate() != null) {
            record.setYearMonth(record.getRecordDate().format(YM_FMT));
        }
    }
}
