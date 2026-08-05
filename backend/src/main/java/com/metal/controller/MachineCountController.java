package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.MachineCount;
import com.metal.service.MachineCountService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/machine-count")
public class MachineCountController {

    @Autowired
    private MachineCountService service;

    @GetMapping
    public Result<PageResult<MachineCount>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String statMonth,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, statMonth, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<MachineCount> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    public Result<MachineCount> create(@RequestBody MachineCount record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/{id}")
    public Result<MachineCount> update(@PathVariable Long id, @RequestBody MachineCount record) {
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
                            @RequestParam(required = false) String statMonth) {
        service.exportExcel(response, companyId, keyword, statMonth);
    }

    @GetMapping("/by-month")
    public Result<java.util.List<MachineCount>> byMonth(@RequestParam String statMonth,
                                                         @RequestParam(required = false) Long companyId) {
        return Result.ok(service.findByMonth(statMonth, companyId));
    }

    @PostMapping("/clear-by-month")
    public Result<String> clearByMonth(@RequestParam String statMonth,
                                       @RequestParam(required = false) Long companyId) {
        int count = service.clearByMonth(statMonth, companyId);
        return Result.ok("已清除 " + statMonth + " 月份数据 " + count + " 条（基准线保留）");
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }
}
