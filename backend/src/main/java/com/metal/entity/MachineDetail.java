package com.metal.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class MachineDetail {
    @ExcelIgnore
    private Long id;
    @ExcelIgnore
    private Long companyId;
    @ExcelProperty(value = "厂房", index = 0)
    private String factory;
    @ExcelProperty(value = "机台号", index = 1)
    private String machineNo;
    @ExcelIgnore
    private String plantMachine;
    @ExcelProperty(value = "机台品牌", index = 2)
    private String machineBrand;
    @ExcelIgnore
    private String createdBy;
    @ExcelIgnore
    private String updatedBy;
}
