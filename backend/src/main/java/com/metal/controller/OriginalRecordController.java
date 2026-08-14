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

    @Autowired
    private com.metal.service.OssService ossService;

    /** 上传维修图片到 OSS，返回 object key（私有读，展示时经 image-url 接口签临时 URL）。
     *  id 为维修记录主键：非空时以 id 命名图片（original-record/{yyyyMMdd}/{id}.{ext}） */
    @PostMapping("/upload-image")
    public Result<java.util.Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file,
                                                             @RequestParam(required = false) Long id) {
        String key = ossService.upload(file, id);
        return Result.ok(java.util.Map.of("key", key));
    }

    /** 按 key 生成 OSS 临时访问 URL（1 小时有效），仅允许 original-record/ 前缀 */
    @GetMapping("/image-url")
    public Result<java.util.Map<String, String>> imageUrl(@RequestParam String key) {
        return Result.ok(java.util.Map.of("url", ossService.signUrl(key)));
    }

    /** 删除 OSS 图片（前端换图清理用；记录删除不级联，防误删） */
    @DeleteMapping("/image")
    public Result<Void> deleteImage(@RequestParam String key) {
        ossService.delete(key);
        return Result.ok();
    }

    @GetMapping
    public Result<PageResult<OriginalRecord>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(required = false) Boolean excludeLinked) {
        return Result.ok(service.query(page, pageSize, companyId, keyword, shift, factory,
                startDate, endDate, sortField, sortOrder, excludeLinked));
    }

    @GetMapping("/{id}")
    public Result<OriginalRecord> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    /** 某维修记录已关联的未过保物料条数（前端删除提示用） */
    @GetMapping("/{id}/linked-count")
    public Result<java.util.Map<String, Object>> linkedCount(@PathVariable Long id) {
        return Result.ok(java.util.Map.of("count", service.linkedCount(id)));
    }

    /** 多条维修记录的关联未过保物料总数（前端批量删除提示用） */
    @PostMapping("/linked-counts")
    public Result<java.util.Map<String, Object>> linkedCounts(@RequestBody BatchDeleteDTO dto) {
        return Result.ok(java.util.Map.of("count", service.linkedCounts(dto.getIds())));
    }

    @GetMapping("/copy/{id}")
    public Result<OriginalRecord> copy(@PathVariable Long id) {
        return Result.ok(service.copy(id));
    }

    @GetMapping("/lookup-156")
    public Result<java.util.Map<String, String>> lookupFrom156(@RequestParam String materialCode,
                                                               @RequestParam(required = false) Long companyId) {
        return Result.ok(service.lookupFrom156(materialCode, companyId));
    }

    @GetMapping("/lookup-delivery-ref")
    public Result<java.util.Map<String, Object>> lookupDeliveryRef(
            @RequestParam String machineOnMaterial,
            @RequestParam(required = false) String recordDate,
            @RequestParam(required = false) Long companyId) {
        return Result.ok(service.lookupDeliveryRef(machineOnMaterial, recordDate, companyId));
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
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate) {
        service.exportExcel(response, companyId, keyword, shift, factory, startDate, endDate);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }
}
