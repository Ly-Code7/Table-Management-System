package com.metal.controller;

import com.metal.common.PageResult;
import com.metal.common.Result;
import com.metal.dto.BatchDeleteDTO;
import com.metal.dto.ImportResultDTO;
import com.metal.entity.DeliveryStats;
import com.metal.entity.DeliveryStatsDaily;
import com.metal.service.DeliveryStatsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/delivery-stats")
public class DeliveryStatsController {

    @Autowired
    private DeliveryStatsService service;

    @GetMapping
    public Result<PageResult<DeliveryStats>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        // 日期区间模式：实时统计 + 跨月料号合并为一行，全量返回（前端自行切片分页）
        if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            List<DeliveryStats> rows = service.queryRange(companyId, keyword, category, startDate, endDate);
            return Result.ok(new PageResult<>((long) rows.size(), 1, Math.max(rows.size(), 1), rows));
        }
        return Result.ok(service.query(page, pageSize, companyId, keyword, category, yearMonth, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<DeliveryStats> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @GetMapping("/{id}/dailies")
    public Result<List<DeliveryStatsDaily>> getDailies(@PathVariable Long id) {
        return Result.ok(service.getDailies(id));
    }

    @PostMapping
    public Result<DeliveryStats> create(@RequestBody Map<String, Object> body) {
        DeliveryStats record = parseStats(body);
        List<DeliveryStatsDaily> dailies = parseDailies(body);
        return Result.ok(service.create(record, dailies));
    }

    @PutMapping("/{id}")
    public Result<DeliveryStats> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        DeliveryStats record = parseStats(body);
        record.setId(id);
        List<DeliveryStatsDaily> dailies = parseDailies(body);
        return Result.ok(service.update(record, dailies));
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
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String yearMonth,
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate) {
        service.exportExcel(response, companyId, keyword, category, yearMonth, startDate, endDate);
    }

    @PostMapping("/batch-refresh")
    public Result<java.util.Map<String, Object>> batchRefresh(@RequestBody java.util.Map<String, Object> body) {
        String yearMonth = (String) body.get("yearMonth");
        String statMonth = (String) body.get("statMonth");
        Long companyId = body.get("companyId") != null ? Long.valueOf(body.get("companyId").toString()) : null;
        int count = service.batchRefreshByMonth(yearMonth, statMonth, companyId);
        return Result.ok(java.util.Map.of("msg", "已更新 " + count + " 条记录"));
    }

    @GetMapping("/auto-fill")
    public Result<java.util.Map<String, Object>> autoFill(
            @RequestParam String materialCode,
            @RequestParam(required = false) String statDate,
            @RequestParam(required = false) Long companyId) {
        return Result.ok(service.autoFill(materialCode, statDate, companyId));
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        service.downloadTemplate(response);
    }

    // 简单手动映射，避免引入 Jackson 复杂配置
    @SuppressWarnings("unchecked")
    private DeliveryStats parseStats(Map<String, Object> body) {
        DeliveryStats s = new DeliveryStats();
        s.setCategory((String) body.get("category"));
        s.setMaterialCode((String) body.get("materialCode"));
        s.setSystemName((String) body.get("systemName"));
        s.setPartName((String) body.get("partName"));
        if (body.get("unitUsage") != null) s.setUnitUsage(new java.math.BigDecimal(body.get("unitUsage").toString()));
        if (body.get("ratio") != null) s.setRatio(new java.math.BigDecimal(body.get("ratio").toString()));
        if (body.get("unitPriceWithTax") != null) s.setUnitPriceWithTax(new java.math.BigDecimal(body.get("unitPriceWithTax").toString()));
        if (body.get("machineCount") != null) s.setMachineCount(Integer.valueOf(body.get("machineCount").toString()));
        if (body.get("deliveryQuantity") != null) s.setDeliveryQuantity(Integer.valueOf(body.get("deliveryQuantity").toString()));
        if (body.get("machineOnQuantity") != null) s.setMachineOnQuantity(Integer.valueOf(body.get("machineOnQuantity").toString()));
        if (body.get("monthRepair") != null) s.setMonthRepair(Integer.valueOf(body.get("monthRepair").toString()));
        if (body.get("statDate") != null) s.setStatDate(java.time.LocalDate.parse(body.get("statDate").toString()));
        s.setYearMonth((String) body.get("yearMonth"));
        if (body.get("companyId") != null) s.setCompanyId(Long.valueOf(body.get("companyId").toString()));
        return s;
    }

    @SuppressWarnings("unchecked")
    private List<DeliveryStatsDaily> parseDailies(Map<String, Object> body) {
        Object dailiesObj = body.get("dailies");
        if (dailiesObj == null) return null;
        List<Map<String, Object>> list = (List<Map<String, Object>>) dailiesObj;
        java.util.ArrayList<DeliveryStatsDaily> result = new java.util.ArrayList<>();
        for (Map<String, Object> item : list) {
            DeliveryStatsDaily d = new DeliveryStatsDaily();
            if (item.get("dayNumber") != null) d.setDayNumber(Integer.valueOf(item.get("dayNumber").toString()));
            if (item.get("value") != null) d.setValue(new java.math.BigDecimal(item.get("value").toString()));
            result.add(d);
        }
        return result;
    }
}
