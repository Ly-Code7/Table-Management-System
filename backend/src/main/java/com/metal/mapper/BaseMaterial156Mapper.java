package com.metal.mapper;

import com.metal.entity.BaseMaterial156;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface BaseMaterial156Mapper {

    @Select("SELECT * FROM base_material_156 WHERE id = #{id}")
    BaseMaterial156 findById(Long id);

    @Select("SELECT COUNT(*) FROM base_material_156 WHERE material_code = #{materialCode} AND company_id = #{companyId}")
    int countByMaterialCode(@Param("materialCode") String materialCode, @Param("companyId") Long companyId);

    @Insert("INSERT INTO base_material_156 (company_id, category, material_code, system_name, part_name, " +
            "unit_usage, ratio, unit_price_with_tax, created_by, updated_by) " +
            "VALUES (#{companyId}, #{category}, #{materialCode}, #{systemName}, #{partName}, " +
            "#{unitUsage}, #{ratio}, #{unitPriceWithTax}, #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BaseMaterial156 record);

    @Update("UPDATE base_material_156 SET category=#{category}, material_code=#{materialCode}, " +
            "system_name=#{systemName}, part_name=#{partName}, unit_usage=#{unitUsage}, " +
            "ratio=#{ratio}, unit_price_with_tax=#{unitPriceWithTax}, updated_by=#{updatedBy} WHERE id=#{id}")
    int update(BaseMaterial156 record);

    @Delete("DELETE FROM base_material_156 WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM base_material_156 WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    @Insert("<script>" +
            "INSERT INTO base_material_156 (company_id, category, material_code, system_name, part_name, " +
            "unit_usage, ratio, unit_price_with_tax, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.category}, #{r.materialCode}, #{r.systemName}, #{r.partName}, " +
            "#{r.unitUsage}, #{r.ratio}, #{r.unitPriceWithTax}, #{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<BaseMaterial156> records);

    @Select("<script>" +
            "SELECT * FROM base_material_156 WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (material_code LIKE CONCAT('%',#{keyword},'%') OR system_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR part_name LIKE CONCAT('%',#{keyword},'%') OR category LIKE CONCAT('%',#{keyword},'%') OR id LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "ORDER BY ${sortField} ${sortOrder} " +
            "</script>")
    List<BaseMaterial156> search(@Param("companyId") Long companyId, @Param("keyword") String keyword,
                                  @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    @Select("<script>SELECT * FROM base_material_156 WHERE " +
            "(material_code LIKE CONCAT('%',#{keyword},'%') " +
            "OR system_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR part_name LIKE CONCAT('%',#{keyword},'%')) " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> LIMIT 15</script>")
    List<BaseMaterial156> searchByKeyword(@Param("keyword") String keyword, @Param("companyId") Long companyId);

    @Select("SELECT * FROM base_material_156 WHERE material_code = #{materialCode} LIMIT 1")
    BaseMaterial156 findByMaterialCode(@Param("materialCode") String materialCode);
}
