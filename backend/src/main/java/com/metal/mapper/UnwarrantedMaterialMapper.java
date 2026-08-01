package com.metal.mapper;

import com.metal.entity.UnwarrantedMaterial;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface UnwarrantedMaterialMapper {

    @Select("SELECT * FROM unwarranted_material WHERE id = #{id}")
    UnwarrantedMaterial findById(Long id);

    @Insert("INSERT INTO unwarranted_material (company_id, record_date, factory, machine_no, " +
            "equip_repair_debugging, repair_material_on, repair_person, warranty_status, part_name, quantity, material_code, " +
            "unique_id, last_date_no, current_date_no, plant_machine, `year_month`, repair_amount, " +
            "total_count, occurrence_no, last_date, `current_date`, over_six_months, usage_months, last_repair_person, " +
            "original_record_id, category, created_by, updated_by) VALUES " +
            "(#{companyId}, #{recordDate}, #{factory}, #{machineNo}, " +
            "#{equipRepairDebugging}, #{repairMaterialOn}, #{repairPerson}, #{warrantyStatus}, #{partName}, #{quantity}, #{materialCode}, " +
            "#{uniqueId}, #{lastDateNo}, #{currentDateNo}, #{plantMachine}, #{yearMonth}, #{repairAmount}, " +
            "#{totalCount}, #{occurrenceNo}, #{lastDate}, #{currentDate}, #{overSixMonths}, #{usageMonths}, #{lastRepairPerson}, " +
            "#{originalRecordId}, #{category}, #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UnwarrantedMaterial record);

    @Update("UPDATE unwarranted_material SET record_date=#{recordDate}, factory=#{factory}, machine_no=#{machineNo}, " +
            "equip_repair_debugging=#{equipRepairDebugging}, repair_material_on=#{repairMaterialOn}, repair_person=#{repairPerson}, " +
            "warranty_status=#{warrantyStatus}, part_name=#{partName}, quantity=#{quantity}, material_code=#{materialCode}, " +
            "unique_id=#{uniqueId}, last_date_no=#{lastDateNo}, current_date_no=#{currentDateNo}, plant_machine=#{plantMachine}, " +
            "`year_month`=#{yearMonth}, repair_amount=#{repairAmount}, total_count=#{totalCount}, occurrence_no=#{occurrenceNo}, " +
            "last_date=#{lastDate}, `current_date`=#{currentDate}, over_six_months=#{overSixMonths}, usage_months=#{usageMonths}, " +
            "last_repair_person=#{lastRepairPerson}, original_record_id=#{originalRecordId}, category=#{category}, " +
            "updated_by=#{updatedBy} WHERE id=#{id}")
    int update(UnwarrantedMaterial record);

    @Delete("DELETE FROM unwarranted_material WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM unwarranted_material WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    @Select("<script>" +
            "SELECT * FROM unwarranted_material WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (factory LIKE CONCAT('%',#{keyword},'%') OR machine_no LIKE CONCAT('%',#{keyword},'%') " +
            "OR part_name LIKE CONCAT('%',#{keyword},'%') OR material_code LIKE CONCAT('%',#{keyword},'%') " +
            "OR repair_person LIKE CONCAT('%',#{keyword},'%') OR unique_id LIKE CONCAT('%',#{keyword},'%') " +
            "OR plant_machine LIKE CONCAT('%',#{keyword},'%') OR equip_repair_debugging LIKE CONCAT('%',#{keyword},'%') " +
            "OR repair_material_on LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='factory != null and factory != \"\"'>AND factory = #{factory}</if> " +
            "<if test='warrantyStatus != null and warrantyStatus != \"\"'>AND warranty_status = #{warrantyStatus}</if> " +
            "<if test='startDate != null'>AND record_date &gt;= #{startDate}</if> " +
            "<if test='endDate != null'>AND record_date &lt;= #{endDate}</if> " +
            "ORDER BY ${sortField} ${sortOrder} " +
            "</script>")
    List<UnwarrantedMaterial> search(@Param("companyId") Long companyId, @Param("keyword") String keyword,
                                     @Param("factory") String factory,
                                     @Param("warrantyStatus") String warrantyStatus,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate,
                                     @Param("sortField") String sortField,
                                     @Param("sortOrder") String sortOrder);

    /** 统计同一唯一标识编号已出现的次数（可排除当前记录，用于计算"第几次"） */
    @Select("<script>SELECT COUNT(*) FROM unwarranted_material " +
            "WHERE unique_id = #{uniqueId} AND company_id = #{companyId} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByUniqueId(@Param("uniqueId") String uniqueId, @Param("companyId") Long companyId,
                        @Param("excludeId") Long excludeId);

    /** 查询同一唯一标识编号的上一条记录（按日期倒序取最近一条，可排除当前记录） */
    @Select("<script>SELECT * FROM unwarranted_material " +
            "WHERE unique_id = #{uniqueId} AND company_id = #{companyId} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if> " +
            "ORDER BY record_date DESC, id DESC LIMIT 1</script>")
    UnwarrantedMaterial findLatestByUniqueId(@Param("uniqueId") String uniqueId, @Param("companyId") Long companyId,
                                             @Param("excludeId") Long excludeId);

    /** 批量插入（每批最多 500 条，提升大数据量导入性能） */
    @Insert("<script>" +
            "INSERT INTO unwarranted_material (company_id, record_date, factory, machine_no, " +
            "equip_repair_debugging, repair_material_on, repair_person, warranty_status, part_name, quantity, material_code, " +
            "unique_id, last_date_no, current_date_no, plant_machine, `year_month`, repair_amount, " +
            "total_count, occurrence_no, last_date, `current_date`, over_six_months, usage_months, last_repair_person, " +
            "original_record_id, category, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.recordDate}, #{r.factory}, #{r.machineNo}, " +
            "#{r.equipRepairDebugging}, #{r.repairMaterialOn}, #{r.repairPerson}, #{r.warrantyStatus}, #{r.partName}, #{r.quantity}, #{r.materialCode}, " +
            "#{r.uniqueId}, #{r.lastDateNo}, #{r.currentDateNo}, #{r.plantMachine}, #{r.yearMonth}, #{r.repairAmount}, " +
            "#{r.totalCount}, #{r.occurrenceNo}, #{r.lastDate}, #{r.currentDate}, #{r.overSixMonths}, #{r.usageMonths}, #{r.lastRepairPerson}, " +
            "#{r.originalRecordId}, #{r.category}, #{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<UnwarrantedMaterial> records);

    /** 统计同一维修记录已被关联的次数（可排除当前记录，用于校验一个维修记录只允许关联一条未过保物料） */
    @Select("<script>SELECT COUNT(*) FROM unwarranted_material WHERE original_record_id = #{originalRecordId} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByOriginalRecordId(@Param("originalRecordId") Long originalRecordId,
                                @Param("excludeId") Long excludeId);
}
