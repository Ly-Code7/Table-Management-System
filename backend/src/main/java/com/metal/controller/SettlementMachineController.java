package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.SettlementMachine;
import com.metal.service.SettlementMachineService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settlement-machine")
public class SettlementMachineController {

    @Autowired
    private SettlementMachineService service;

    @GetMapping
    public Result<PageResult<SettlementMachine>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String machineModel,
            @RequestParam(required = false) String statMonth,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, machineModel, statMonth, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<SettlementMachine> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    public Result<SettlementMachine> create(@RequestBody SettlementMachine record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/{id}")
    public Result<SettlementMachine> update(@PathVariable Long id, @RequestBody SettlementMachine record) {
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
                            @RequestParam(required = false) String machineModel) {
        service.exportExcel(response, companyId, keyword, machineModel);
    }

    @GetMapping("/lookup-156")
    public Result<java.util.Map<String, Object>> lookupFrom156(@RequestParam String materialCode,
                                                               @RequestParam(required = false) Long companyId) {
        return Result.ok(service.lookupFrom156(materialCode, companyId));
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }
}
