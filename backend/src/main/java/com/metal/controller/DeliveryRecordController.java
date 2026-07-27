package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.DeliveryRecord;
import com.metal.service.DeliveryRecordService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/delivery-record")
public class DeliveryRecordController {

    @Autowired
    private DeliveryRecordService service;

    @GetMapping
    public Result<PageResult<DeliveryRecord>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String productAttr,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, category, productAttr,
                factory, startDate, endDate, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<DeliveryRecord> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    public Result<DeliveryRecord> create(@RequestBody DeliveryRecord record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/{id}")
    public Result<DeliveryRecord> update(@PathVariable Long id, @RequestBody DeliveryRecord record) {
        record.setId(id);
        return Result.ok(service.update(record));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody BatchDeleteDTO dto) {
        service.batchDelete(dto.getIds());
        return Result.ok();
    }

    @GetMapping("/copy/{id}")
    public Result<DeliveryRecord> copy(@PathVariable Long id) {
        return Result.ok(service.copy(id));
    }

    /** 根据物料编码查询最近的送货记录，用于新增时自动回填 */
    @GetMapping("/lookup-by-code")
    public Result<DeliveryRecord> lookupByCode(@RequestParam String materialCode) {
        return Result.ok(service.getLatestByMaterialCode(materialCode));
    }

    @PostMapping("/import")
    public Result<ImportResultDTO> importExcel(@RequestParam("file") MultipartFile file,
                                               @RequestParam(required = false) Long companyId) {
        return Result.ok(service.importExcel(file, companyId));
    }

    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) Long companyId,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String productAttr,
                            @RequestParam(required = false) String factory,
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate) {
        service.exportExcel(response, companyId, keyword, category, productAttr, factory, startDate, endDate);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }
}
