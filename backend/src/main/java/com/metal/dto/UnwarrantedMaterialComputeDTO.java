package com.metal.dto;

import lombok.Data;

/**
 * 未过保物料 — 派生字段实时计算预览结果（纯读，不写库）。
 * 字段命名与前端表单一致，Jackson 序列化为 JSON 供前端 Object.assign 回填。
 */
@Data
public class UnwarrantedMaterialComputeDTO {
    private String category;
    private String uniqueId;
    private String plantMachine;
    private String yearMonth;
    private String currentDate;
    private Integer occurrenceNo;
    private Integer totalCount;
    private String lastDate;
    private String lastDateNo;
    private String currentDateNo;
    private String overSixMonths;
    private String usageMonths;
    private String lastRepairPerson;
}
