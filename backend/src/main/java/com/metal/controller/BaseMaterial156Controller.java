package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.BaseMaterial156;
import com.metal.service.BaseMaterial156Service;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/base-material-156")
public class BaseMaterial156Controller {

    @Autowired
    private BaseMaterial156Service service;

    @GetMapping
    public Result<PageResult<BaseMaterial156>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, sortField, sortOrder));
    }

    @GetMapping("/search")
    public Result<List<BaseMaterial156>> search(@RequestParam String keyword,
                                                @RequestParam(required = false) Long companyId) {
        return Result.ok(service.searchByKeyword(keyword, companyId));
    }

    @GetMapping("/{id}")
    public Result<BaseMaterial156> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    public Result<BaseMaterial156> create(@RequestBody BaseMaterial156 record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/{id}")
    public Result<BaseMaterial156> update(@PathVariable Long id, @RequestBody BaseMaterial156 record) {
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
                            @RequestParam(required = false) String keyword) {
        service.exportExcel(response, companyId, keyword);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }
}
