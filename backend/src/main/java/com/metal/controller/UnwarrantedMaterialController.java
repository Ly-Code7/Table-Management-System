package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.dto.UnwarrantedMaterialComputeDTO;
import com.metal.dto.UnwarrantedMaterialLookupDTO;
import com.metal.entity.UnwarrantedMaterial;
import com.metal.service.UnwarrantedMaterialService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/unwarranted-material")
public class UnwarrantedMaterialController {

    @Autowired
    private UnwarrantedMaterialService service;

    @GetMapping
    public Result<PageResult<UnwarrantedMaterial>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String warrantyStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, factory, warrantyStatus,
                startDate, endDate, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<UnwarrantedMaterial> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    public Result<UnwarrantedMaterial> create(@RequestBody UnwarrantedMaterial record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/{id}")
    public Result<UnwarrantedMaterial> update(@PathVariable Long id, @RequestBody UnwarrantedMaterial record) {
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
                            @RequestParam(required = false) String factory,
                            @RequestParam(required = false) String warrantyStatus,
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate) {
        service.exportExcel(response, companyId, keyword, factory, warrantyStatus, startDate, endDate);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }

    /** 选择维修记录后回填基础字段（公司内，防止跨公司读取维修记录） */
    @GetMapping("/lookup-original")
    public Result<UnwarrantedMaterialLookupDTO> lookupOriginal(@RequestParam Long id,
                                                               @RequestParam(required = false) Long companyId) {
        return Result.ok(service.lookupOriginal(id, companyId));
    }

    /** 派生字段实时计算预览 */
    @GetMapping("/compute")
    public Result<UnwarrantedMaterialComputeDTO> compute(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String machineNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String recordDate,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long excludeId) {
        return Result.ok(service.compute(factory, machineNo, materialCode, recordDate, quantity, companyId, excludeId));
    }
}
