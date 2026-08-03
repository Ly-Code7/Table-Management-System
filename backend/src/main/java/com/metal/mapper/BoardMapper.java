package com.metal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据看板聚合 Mapper。
 * 聚合结果统一返回 List&lt;Map&gt;：键约定见各方法注释（k=行键、ym=年+月、v=值）。
 */
@Mapper
public interface BoardMapper {

    /** 机台清单：original_record 的 厂房-机台号 去重（与 Excel UNIQUE(总维修明细!B:B) 一致） */
    @Select("SELECT DISTINCT CONCAT(factory,'-',machine_no) AS machine FROM original_record WHERE company_id = #{companyId}")
    List<String> machineList(@Param("companyId") Long companyId);

    /** 料号清单：156 项（含类别/配件名称/含税单价） */
    @Select("SELECT material_code AS code, category, part_name, unit_price_with_tax AS price " +
            "FROM base_material_156 WHERE company_id = #{companyId}")
    List<Map<String, Object>> materialList(@Param("companyId") Long companyId);

    /** 维修金额聚合：未过保物料按（厂房+机台号, 年+月）求和，键 k=plant_machine, ym=year_month, v=SUM(repair_amount) */
    @Select("SELECT plant_machine AS k, `year_month` AS ym, SUM(repair_amount) AS v " +
            "FROM unwarranted_material WHERE company_id = #{companyId} " +
            "AND `year_month` LIKE CONCAT(#{ymPrefix}, '%') AND plant_machine IS NOT NULL " +
            "GROUP BY plant_machine, `year_month`")
    List<Map<String, Object>> repairAmountSum(@Param("companyId") Long companyId,
                                              @Param("ymPrefix") String ymPrefix);

    /** 故障频次聚合：维修记录按（厂房+机台号, 年+月）计数，键 k=CONCAT(factory,'-',machine_no), ym=year_month, v=COUNT(*) */
    @Select("SELECT CONCAT(factory,'-',machine_no) AS k, `year_month` AS ym, COUNT(*) AS v " +
            "FROM original_record WHERE company_id = #{companyId} " +
            "AND `year_month` LIKE CONCAT(#{ymPrefix}, '%') " +
            "GROUP BY CONCAT(factory,'-',machine_no), `year_month`")
    List<Map<String, Object>> faultCount(@Param("companyId") Long companyId,
                                         @Param("ymPrefix") String ymPrefix);

    /** 物料频次聚合：未过保物料按（物料编码, 年+月）计数，键 k=material_code, ym=year_month, v=COUNT(*) */
    @Select("SELECT material_code AS k, `year_month` AS ym, COUNT(*) AS v " +
            "FROM unwarranted_material WHERE company_id = #{companyId} " +
            "AND `year_month` LIKE CONCAT(#{ymPrefix}, '%') AND material_code IS NOT NULL " +
            "GROUP BY material_code, `year_month`")
    List<Map<String, Object>> materialCount(@Param("companyId") Long companyId,
                                            @Param("ymPrefix") String ymPrefix);

    /**
     * 返修频次聚合：非首次维修（有上次日期 last_date IS NOT NULL）按（物料编码, 年+月）计数。
     * 判别依据：Excel 辅助列-T0（返修标记，值=物料编码）非空行 5145/23171（22%），
     * 经用户确认以「非首次维修」近似（覆盖率 96.7%，179 行偏差接受）。
     */
    @Select("SELECT material_code AS k, `year_month` AS ym, COUNT(*) AS v " +
            "FROM unwarranted_material WHERE company_id = #{companyId} " +
            "AND `year_month` LIKE CONCAT(#{ymPrefix}, '%') " +
            "AND material_code IS NOT NULL AND last_date IS NOT NULL " +
            "GROUP BY material_code, `year_month`")
    List<Map<String, Object>> repairCount(@Param("companyId") Long companyId,
                                          @Param("ymPrefix") String ymPrefix);
}
