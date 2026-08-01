package com.metal.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 未过保物料 — 与维修记录（original_record）关联。
 * 新增时通过"选择维修记录"回填基础字段，派生字段（唯一标识编号、第几次、上次日期、
 * 超六个月、使用时长、上次维修人等）由后端 applyCalculations 自动计算。
 */
@Data
public class UnwarrantedMaterial {
    @ExcelIgnore
    private Long id;
    @ExcelIgnore
    private Long companyId;
    @ExcelIgnore
    private Long originalRecordId;
    @ExcelProperty(value = "日期", index = 0)
    @DateTimeFormat("yyyy-MM-dd")
    private LocalDate recordDate;
    @ExcelProperty(value = "厂房", index = 1)
    private String factory;
    @ExcelProperty(value = "机台号", index = 2)
    private String machineNo;
    @ExcelProperty(value = "设备维修调试", index = 3)
    private String equipRepairDebugging;
    @ExcelProperty(value = "维修物料装上", index = 4)
    private String repairMaterialOn;
    @ExcelProperty(value = "维修人", index = 5)
    private String repairPerson;
    @ExcelProperty(value = "未过保", index = 6)
    private String warrantyStatus;
    @ExcelProperty(value = "配件名称", index = 7)
    private String partName;
    @ExcelProperty(value = "数量", index = 8)
    private Integer quantity;
    @ExcelProperty(value = "物料编码", index = 9)
    private String materialCode;
    @ExcelProperty(value = "唯一标识编号", index = 10)
    private String uniqueId;
    @ExcelProperty(value = "上次日期+编号", index = 11)
    private String lastDateNo;
    @ExcelProperty(value = "本次日期+编号", index = 12)
    private String currentDateNo;
    @ExcelProperty(value = "厂房+机台号", index = 13)
    private String plantMachine;
    @ExcelProperty(value = "年+月", index = 14)
    private String yearMonth;
    @ExcelProperty(value = "维修金额（合约）", index = 15)
    private BigDecimal repairAmount;
    @ExcelProperty(value = "总次数", index = 16)
    private Integer totalCount;
    @ExcelProperty(value = "第几次", index = 17)
    private Integer occurrenceNo;
    @ExcelProperty(value = "上次日期", index = 18)
    @DateTimeFormat("yyyy-MM-dd")
    private LocalDate lastDate;
    @ExcelProperty(value = "本次日期", index = 19)
    @DateTimeFormat("yyyy-MM-dd")
    private LocalDate currentDate;
    @ExcelProperty(value = "超六个月", index = 20)
    private String overSixMonths;
    @ExcelProperty(value = "使用时长/月", index = 21)
    private String usageMonths;
    @ExcelProperty(value = "上次维修人", index = 22)
    private String lastRepairPerson;
    @ExcelProperty(value = "类别", index = 23)
    private String category;
    @ExcelIgnore
    private String createdBy;
    @ExcelIgnore
    private String updatedBy;
    @ExcelIgnore
    private LocalDateTime createdAt;
    @ExcelIgnore
    private LocalDateTime updatedAt;
}
