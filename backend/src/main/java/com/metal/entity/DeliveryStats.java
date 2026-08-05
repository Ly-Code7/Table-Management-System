package com.metal.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DeliveryStats {
    /** 主键ID（导入导出时忽略） */
    @ExcelIgnore
    private Long id;
    /** 公司ID（导入导出时忽略） */
    @ExcelIgnore
    private Long companyId;
    /** 类别 */
    @ExcelProperty(value = "类别", index = 0)
    private String category;
    /** 物料编码 */
    @ExcelProperty(value = "物料编码", index = 1)
    private String materialCode;
    /** 系统名称 */
    @ExcelProperty(value = "系统名称", index = 2)
    private String systemName;
    /** 配件名称 */
    @ExcelProperty(value = "配件名称", index = 3)
    private String partName;
    /** 单台机用量 */
    @ExcelProperty(value = "单台机用量", index = 4)
    private BigDecimal unitUsage;
    /** 比例 */
    @ExcelProperty(value = "比例", index = 5)
    private BigDecimal ratio;
    /** 含税单价 */
    @ExcelProperty(value = "含税单价", index = 6)
    private BigDecimal unitPriceWithTax;
    /** 机台数 */
    @ExcelProperty(value = "机台数", index = 7)
    private Integer machineCount;
    /** 送货数量 */
    @ExcelProperty(value = "送货数量", index = 8)
    private Integer deliveryQuantity;
    /** 送货免费（产品属性为"免费"的送货数量，自动统计） */
    @ExcelProperty(value = "送货免费", index = 9)
    private Integer freeDeliveryQuantity;
    /** 上机数量 */
    @ExcelProperty(value = "上机数量", index = 10)
    private Integer machineOnQuantity;
    /** 当月返修 */
    @ExcelProperty(value = "当月返修", index = 11)
    private Integer monthRepair;
    /** 约定比例数量 */
    @ExcelProperty(value = "约定比例数量", index = 12)
    private BigDecimal agreedRatioQuantity;
    /** 超比数量合计 */
    @ExcelProperty(value = "超比数量合计", index = 13)
    private BigDecimal excessQuantity;
    /** 超比含税金额合计 */
    @ExcelProperty(value = "超比含税金额合计", index = 14)
    private BigDecimal excessAmountWithTax;
    /** 统计日期 */
    @ExcelProperty(value = "统计日期", index = 15)
    @DateTimeFormat("yyyy-MM-dd")
    private LocalDate statDate;
    /** 年+月 */
    @ExcelProperty(value = "年+月", index = 16)
    private String yearMonth;
    /** 创建时间（导入导出时忽略） */
    @ExcelIgnore
    private LocalDateTime createdAt;
    /** 更新时间（导入导出时忽略） */
    @ExcelIgnore
    private LocalDateTime updatedAt;
    /** 创建人（导入导出时忽略） */
    @ExcelIgnore
    private String createdBy;
    /** 更新人（导入导出时忽略） */
    @ExcelIgnore
    private String updatedBy;

    // ====== 每日送货明细（仅 Excel 导出/导入用，不持久化） ======
    @ExcelProperty(value = "1号", index = 17) private transient BigDecimal day01;
    @ExcelProperty(value = "2号", index = 18) private transient BigDecimal day02;
    @ExcelProperty(value = "3号", index = 19) private transient BigDecimal day03;
    @ExcelProperty(value = "4号", index = 20) private transient BigDecimal day04;
    @ExcelProperty(value = "5号", index = 21) private transient BigDecimal day05;
    @ExcelProperty(value = "6号", index = 22) private transient BigDecimal day06;
    @ExcelProperty(value = "7号", index = 23) private transient BigDecimal day07;
    @ExcelProperty(value = "8号", index = 24) private transient BigDecimal day08;
    @ExcelProperty(value = "9号", index = 25) private transient BigDecimal day09;
    @ExcelProperty(value = "10号", index = 26) private transient BigDecimal day10;
    @ExcelProperty(value = "11号", index = 27) private transient BigDecimal day11;
    @ExcelProperty(value = "12号", index = 28) private transient BigDecimal day12;
    @ExcelProperty(value = "13号", index = 29) private transient BigDecimal day13;
    @ExcelProperty(value = "14号", index = 30) private transient BigDecimal day14;
    @ExcelProperty(value = "15号", index = 31) private transient BigDecimal day15;
    @ExcelProperty(value = "16号", index = 32) private transient BigDecimal day16;
    @ExcelProperty(value = "17号", index = 33) private transient BigDecimal day17;
    @ExcelProperty(value = "18号", index = 34) private transient BigDecimal day18;
    @ExcelProperty(value = "19号", index = 35) private transient BigDecimal day19;
    @ExcelProperty(value = "20号", index = 36) private transient BigDecimal day20;
    @ExcelProperty(value = "21号", index = 37) private transient BigDecimal day21;
    @ExcelProperty(value = "22号", index = 38) private transient BigDecimal day22;
    @ExcelProperty(value = "23号", index = 39) private transient BigDecimal day23;
    @ExcelProperty(value = "24号", index = 40) private transient BigDecimal day24;
    @ExcelProperty(value = "25号", index = 41) private transient BigDecimal day25;
    @ExcelProperty(value = "26号", index = 42) private transient BigDecimal day26;
    @ExcelProperty(value = "27号", index = 43) private transient BigDecimal day27;
    @ExcelProperty(value = "28号", index = 44) private transient BigDecimal day28;
    @ExcelProperty(value = "29号", index = 45) private transient BigDecimal day29;
    @ExcelProperty(value = "30号", index = 46) private transient BigDecimal day30;
    @ExcelProperty(value = "31号", index = 47) private transient BigDecimal day31;
}
