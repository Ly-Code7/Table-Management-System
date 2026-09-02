package com.metal.dto;

import lombok.Data;

/**
 * 未过保物料 — 选择维修记录后回填的基础字段。
 * 字段命名与前端表单一致，Jackson 序列化为 JSON 供前端 Object.assign 回填。
 */
@Data
public class UnwarrantedMaterialLookupDTO {
    private Long originalRecordId;
    private String recordDate;
    private String factory;
    private String machineNo;
    private String equipRepairDebugging;
    private String repairMaterialOn;
    private String mountJudgement;
    private String repairPerson;
    private String warrantyStatus;
    private String partName;
    private Integer quantity;
    private String materialCode;
    private String yearMonth;
}
