package com.metal.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BaseMaterial156 {
    @ExcelIgnore
    private Long id;
    @ExcelIgnore
    private Long companyId;
    @ExcelProperty(value = "类别", index = 0)
    private String category;
    @ExcelProperty(value = "料号", index = 1)
    private String materialCode;
    @ExcelProperty(value = "系统名称", index = 2)
    private String systemName;
    @ExcelProperty(value = "156项名称", index = 3)
    private String partName;
    @ExcelProperty(value = "配件", index = 4)
    private String accessory;
    @ExcelProperty(value = "单台机用量", index = 5)
    private BigDecimal unitUsage;
    @ExcelProperty(value = "比例", index = 6)
    private BigDecimal ratio;
    @ExcelProperty(value = "含税单价", index = 7)
    private BigDecimal unitPriceWithTax;
    @ExcelIgnore
    private LocalDateTime createdAt;
    @ExcelIgnore
    private LocalDateTime updatedAt;
    @ExcelIgnore
    private String createdBy;
    @ExcelIgnore
    private String updatedBy;
}
