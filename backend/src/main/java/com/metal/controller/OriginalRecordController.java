package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.OriginalRecord;
import com.metal.service.OriginalRecordService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/original-record")
public class OriginalRecordController {

    @Autowired
    private OriginalRecordService service;

    @GetMapping
    public Result<PageResult<OriginalRecord>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String isOutOfWarranty,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, shift, factory,
                isOutOfWarranty, startDate, endDate, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<OriginalRecord> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @GetMapping("/copy/{id}")
    public Result<OriginalRecord> copy(@PathVariable Long id) {
        return Result.ok(service.copy(id));
    }

    @GetMapping("/lookup-warranty")
    public Result<java.util.Map<String, Object>> lookupWarranty(@RequestParam String machineOffMaterial,
                                                                 @RequestParam(required = false) String recordDate) {
        return Result.ok(service.lookupWarranty(machineOffMaterial, recordDate));
    }

    @GetMapping("/lookup-156")
    public Result<java.util.Map<String, String>> lookupFrom156(@RequestParam String materialCode) {
        return Result.ok(service.lookupFrom156(materialCode));
    }

    @GetMapping("/lookup-delivery-ref")
    public Result<java.util.Map<String, Object>> lookupDeliveryRef(
            @RequestParam String machineOnMaterial,
            @RequestParam(required = false) String recordDate) {
        return Result.ok(service.lookupDeliveryRef(machineOnMaterial, recordDate));
    }

    @PostMapping
    public Result<OriginalRecord> create(@RequestBody OriginalRecord record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/{id}")
    public Result<OriginalRecord> update(@PathVariable Long id, @RequestBody OriginalRecord record) {
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

    @PostMapping("/import")
    public Result<ImportResultDTO> importExcel(@RequestParam("file") MultipartFile file,
                                               @RequestParam(required = false) Long companyId) {
        return Result.ok(service.importExcel(file, companyId));
    }

    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) Long companyId,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String shift,
                            @RequestParam(required = false) String factory,
                            @RequestParam(required = false) String isOutOfWarranty,
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate) {
        service.exportExcel(response, companyId, keyword, shift, factory, isOutOfWarranty, startDate, endDate);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }
}
