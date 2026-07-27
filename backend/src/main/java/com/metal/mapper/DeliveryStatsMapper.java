package com.metal.mapper;

import com.metal.entity.DeliveryStats;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DeliveryStatsMapper {

    @Select("SELECT * FROM delivery_stats WHERE id = #{id}")
    DeliveryStats findById(Long id);

    @Insert("INSERT INTO delivery_stats (company_id, category, material_code, system_name, part_name, unit_usage, ratio, " +
            "unit_price_with_tax, machine_count, delivery_quantity, machine_on_quantity, month_repair, " +
            "agreed_ratio_quantity, excess_quantity, excess_amount_with_tax, stat_date, `year_month`, created_by, updated_by) " +
            "VALUES (#{companyId}, #{category}, #{materialCode}, #{systemName}, #{partName}, #{unitUsage}, #{ratio}, " +
            "#{unitPriceWithTax}, #{machineCount}, #{deliveryQuantity}, #{machineOnQuantity}, #{monthRepair}, " +
            "#{agreedRatioQuantity}, #{excessQuantity}, #{excessAmountWithTax}, #{statDate}, #{yearMonth}, #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeliveryStats record);

    @Update("UPDATE delivery_stats SET category=#{category}, material_code=#{materialCode}, " +
            "system_name=#{systemName}, part_name=#{partName}, unit_usage=#{unitUsage}, ratio=#{ratio}, " +
            "unit_price_with_tax=#{unitPriceWithTax}, machine_count=#{machineCount}, " +
            "delivery_quantity=#{deliveryQuantity}, machine_on_quantity=#{machineOnQuantity}, " +
            "month_repair=#{monthRepair}, agreed_ratio_quantity=#{agreedRatioQuantity}, " +
            "excess_quantity=#{excessQuantity}, excess_amount_with_tax=#{excessAmountWithTax}, " +
            "stat_date=#{statDate}, `year_month`=#{yearMonth}, updated_by=#{updatedBy} WHERE id=#{id}")
    int update(DeliveryStats record);

    @Delete("DELETE FROM delivery_stats WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM delivery_stats WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    /** 批量插入 */
    @Insert("<script>" +
            "INSERT INTO delivery_stats (company_id, category, material_code, system_name, part_name, unit_usage, ratio, " +
            "unit_price_with_tax, machine_count, delivery_quantity, machine_on_quantity, month_repair, " +
            "agreed_ratio_quantity, excess_quantity, excess_amount_with_tax, stat_date, `year_month`, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.category}, #{r.materialCode}, #{r.systemName}, #{r.partName}, #{r.unitUsage}, #{r.ratio}, " +
            "#{r.unitPriceWithTax}, #{r.machineCount}, #{r.deliveryQuantity}, #{r.machineOnQuantity}, #{r.monthRepair}, " +
            "#{r.agreedRatioQuantity}, #{r.excessQuantity}, #{r.excessAmountWithTax}, #{r.statDate}, #{r.yearMonth}, " +
            "#{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<DeliveryStats> records);

    @Select("<script>" +
            "SELECT * FROM delivery_stats WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (material_code LIKE CONCAT('%',#{keyword},'%') OR system_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR part_name LIKE CONCAT('%',#{keyword},'%') OR category LIKE CONCAT('%',#{keyword},'%') " +
            "OR `year_month` LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='category != null and category != \"\"'>AND category = #{category}</if> " +
            "<if test='yearMonth != null and yearMonth != \"\"'>AND `year_month` = #{yearMonth}</if> " +
            "ORDER BY ${sortField} ${sortOrder} " +
            "</script>")
    List<DeliveryStats> search(@Param("companyId") Long companyId, @Param("keyword") String keyword,
                               @Param("category") String category,
                               @Param("yearMonth") String yearMonth,
                               @Param("sortField") String sortField,
                               @Param("sortOrder") String sortOrder);

    @Select("<script>" +
            "SELECT * FROM delivery_stats WHERE `year_month` = #{yearMonth} " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "</script>")
    List<DeliveryStats> findByYearMonth(@Param("yearMonth") String yearMonth, @Param("companyId") Long companyId);

    @Select("SELECT COUNT(*) FROM delivery_stats WHERE material_code = #{materialCode} AND `year_month` = #{yearMonth}")
    int countByMaterialCodeAndYearMonth(@Param("materialCode") String materialCode, @Param("yearMonth") String yearMonth);

    @Select("SELECT MAX(id) FROM delivery_stats")
    Long findMaxId();

    @Select("SELECT * FROM delivery_stats WHERE id > #{id} ORDER BY id")
    List<DeliveryStats> findByIdGreaterThan(@Param("id") Long id);
}
