package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.Material;
import com.metal.service.MaterialService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/material")
public class MaterialController {

    @Autowired
    private MaterialService service;

    @GetMapping
    public Result<PageResult<Material>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.query(page, pageSize, companyId, keyword));
    }

    @GetMapping("/{id}")
    public Result<Material> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @GetMapping("/search")
    public Result<List<Material>> search(@RequestParam String keyword,
                                         @RequestParam(required = false) Long companyId) {
        return Result.ok(service.searchByKeyword(keyword, companyId));
    }

    @PostMapping
    public Result<Material> create(@RequestBody Material material) {
        return Result.ok(service.create(material));
    }

    @PutMapping("/{id}")
    public Result<Material> update(@PathVariable Long id, @RequestBody Material material) {
        material.setId(id);
        return Result.ok(service.update(material));
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
