package com.metal.mapper;

import com.metal.entity.Material;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MaterialMapper {

    @Select("SELECT * FROM material ORDER BY id")
    List<Material> findAll();

    @Select("SELECT * FROM material WHERE id = #{id}")
    Material findById(Long id);

    @Insert("INSERT INTO material (company_id, category, material_name, spec_model, material_code, remark, created_by, updated_by) " +
            "VALUES (#{companyId}, #{category}, #{materialName}, #{specModel}, #{materialCode}, #{remark}, #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Material material);

    @Update("UPDATE material SET category=#{category}, material_name=#{materialName}, " +
            "spec_model=#{specModel}, material_code=#{materialCode}, remark=#{remark}, updated_by=#{updatedBy} WHERE id=#{id}")
    int update(Material material);

    @Delete("DELETE FROM material WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM material WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    @Select("<script>" +
            "SELECT * FROM material WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (material_code LIKE CONCAT('%',#{keyword},'%') OR material_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR spec_model LIKE CONCAT('%',#{keyword},'%') OR category LIKE CONCAT('%',#{keyword},'%') " +
            "OR remark LIKE CONCAT('%',#{keyword},'%') OR id LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "ORDER BY ${sortField} ${sortOrder} " +
            "</script>")
    List<Material> search(@Param("companyId") Long companyId, @Param("keyword") String keyword,
                          @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    @Select("SELECT * FROM material WHERE material_code LIKE CONCAT('%',#{keyword},'%') " +
            "OR material_name LIKE CONCAT('%',#{keyword},'%') LIMIT 15")
    List<Material> searchByKeyword(@Param("keyword") String keyword);

    /** 按物料编码精确查询（公司内），用于未过保物料自动回填类别 */
    @Select("<script>" +
            "SELECT * FROM material WHERE material_code = #{materialCode} " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> LIMIT 1" +
            "</script>")
    Material findByCode(@Param("materialCode") String materialCode, @Param("companyId") Long companyId);

    /** 按备注精确查询（公司内），用于新增物料时校验备注唯一 */
    @Select("<script>" +
            "SELECT * FROM material WHERE remark = #{remark} " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> LIMIT 1" +
            "</script>")
    Material findByRemark(@Param("remark") String remark, @Param("companyId") Long companyId);

    /** 批量插入 */
    @Insert("<script>" +
            "INSERT INTO material (company_id, category, material_name, spec_model, material_code, remark, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.category}, #{r.materialName}, #{r.specModel}, #{r.materialCode}, #{r.remark}, #{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<Material> records);
}
