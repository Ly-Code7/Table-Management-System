package com.metal.mapper;

import com.metal.entity.SettlementMachine;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SettlementMachineMapper {

    @Select("SELECT * FROM settlement_machine WHERE id = #{id}")
    SettlementMachine findById(Long id);

    @Insert("INSERT INTO settlement_machine (company_id, material_code, category, part_name, unit_usage, ratio, " +
            "unit_price_with_tax, warranty_period, price_type, remark, machine_model, settlement_machine_count, stat_month, created_by, updated_by) " +
            "VALUES (#{companyId}, #{materialCode}, #{category}, #{partName}, #{unitUsage}, #{ratio}, " +
            "#{unitPriceWithTax}, #{warrantyPeriod}, #{priceType}, #{remark}, #{machineModel}, #{settlementMachineCount}, #{statMonth}, #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SettlementMachine record);

    @Update("UPDATE settlement_machine SET material_code=#{materialCode}, category=#{category}, " +
            "part_name=#{partName}, unit_usage=#{unitUsage}, ratio=#{ratio}, unit_price_with_tax=#{unitPriceWithTax}, " +
            "warranty_period=#{warrantyPeriod}, price_type=#{priceType}, remark=#{remark}, " +
            "machine_model=#{machineModel}, settlement_machine_count=#{settlementMachineCount}, " +
            "stat_month=#{statMonth}, updated_by=#{updatedBy} WHERE id=#{id}")
    int update(SettlementMachine record);

    @Delete("DELETE FROM settlement_machine WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM settlement_machine WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    @Insert("<script>" +
            "INSERT INTO settlement_machine (company_id, material_code, category, part_name, unit_usage, ratio, " +
            "unit_price_with_tax, warranty_period, price_type, remark, machine_model, settlement_machine_count, stat_month, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.materialCode}, #{r.category}, #{r.partName}, #{r.unitUsage}, #{r.ratio}, " +
            "#{r.unitPriceWithTax}, #{r.warrantyPeriod}, #{r.priceType}, #{r.remark}, #{r.machineModel}, #{r.settlementMachineCount}, #{r.statMonth}, #{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<SettlementMachine> records);

    @Select("<script>" +
            "SELECT * FROM settlement_machine WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (material_code LIKE CONCAT('%',#{keyword},'%') OR part_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR category LIKE CONCAT('%',#{keyword},'%') OR machine_model LIKE CONCAT('%',#{keyword},'%') OR id LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='machineModel != null and machineModel != \"\"'>AND machine_model = #{machineModel}</if> " +
            "<if test='statMonth != null and statMonth != \"\"'>AND stat_month = #{statMonth}</if> " +
            "ORDER BY ${sortField} ${sortOrder} " +
            "</script>")
    List<SettlementMachine> search(@Param("companyId") Long companyId, @Param("keyword") String keyword,
                                   @Param("machineModel") String machineModel,
                                   @Param("statMonth") String statMonth,
                                   @Param("sortField") String sortField,
                                   @Param("sortOrder") String sortOrder);

    @Select("SELECT SUM(settlement_machine_count) FROM settlement_machine " +
            "WHERE material_code = #{materialCode} AND stat_month = #{statMonth} AND company_id = #{companyId}")
    Integer sumMachineCountByMaterialCodeAndMonth(@Param("materialCode") String materialCode,
                                                   @Param("statMonth") String statMonth,
                                                   @Param("companyId") Long companyId);
}
