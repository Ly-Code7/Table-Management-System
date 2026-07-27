package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.MachineMaterial;
import com.metal.service.MachineMaterialService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/machine-material")
public class MachineMaterialController {

    @Autowired
    private MachineMaterialService service;

    @GetMapping
    public Result<PageResult<MachineMaterial>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String isOutOfWarranty,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, factory,
                isOutOfWarranty, startDate, endDate, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<MachineMaterial> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    public Result<MachineMaterial> create(@RequestBody MachineMaterial record) {
        return Result.ok(service.create(record));
    }

    @GetMapping("/lookup-warranty")
    public Result<java.util.Map<String, Object>> lookupWarranty(@RequestParam String machineOffMaterial) {
        return Result.ok(service.lookupWarranty(machineOffMaterial));
    }

    /** 根据上机物料号查询送货记录，回填料号和送货记录引用 */
    @GetMapping("/lookup-delivery")
    public Result<java.util.Map<String, Object>> lookupDelivery(@RequestParam String machineOnMaterial) {
        return Result.ok(service.lookupDeliveryByMaterial(machineOnMaterial));
    }

    @PutMapping("/{id}")
    public Result<MachineMaterial> update(@PathVariable Long id, @RequestBody MachineMaterial record) {
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
                            @RequestParam(required = false) String isOutOfWarranty,
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate) {
        service.exportExcel(response, companyId, keyword, factory, isOutOfWarranty, startDate, endDate);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }
}
