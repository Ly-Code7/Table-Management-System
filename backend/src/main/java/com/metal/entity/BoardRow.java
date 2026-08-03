package com.metal.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 看板行 DTO（矩阵一行）。
 * 机台类看板（维修金额/故障频次）用 key=factory+'-'+machineNo、factory；
 * 料号类看板（物料频次/返修频次/返修率）用 key=materialCode、category、partName、price。
 * months：月份列（如 FY2601 -> 值），total 为合计/小计，amount 为金额（单价×合计），average 为返修率平均。
 */
@Data
public class BoardRow {
    /** 行主键：机台（厂房-机台号）或料号 */
    private String key;
    /** 机台类看板：厂房（机台号 '-' 前缀） */
    private String factory;
    /** 料号类看板：类别 */
    private String category;
    /** 料号类看板：配件名称 */
    private String partName;
    /** 料号类看板：合约单价（156 项含税单价） */
    private BigDecimal price;
    /** 月份列：FYxx01~FYxx12 -> 值 */
    private Map<String, BigDecimal> months = new LinkedHashMap<>();
    /** 合计/小计：12 个月求和 */
    private BigDecimal total;
    /** 金额：单价 × 合计（仅料号类看板） */
    private BigDecimal amount;
    /** 平均（仅返修率看板：合计返修 ÷ 合计物料） */
    private BigDecimal average;
}
