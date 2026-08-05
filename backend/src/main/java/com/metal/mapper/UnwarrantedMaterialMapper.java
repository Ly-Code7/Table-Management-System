package com.metal.mapper;

import com.metal.entity.UnwarrantedMaterial;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
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
            "OR repair_material_on LIKE CONCAT('%',#{keyword},'%') OR id LIKE CONCAT('%',#{keyword},'%')) " +
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

    /** 统计同一唯一标识编号已出现的总次数（含所有日期，可排除当前记录，用于计算"总次数"） */
    @Select("<script>SELECT COUNT(*) FROM unwarranted_material " +
            "WHERE unique_id = #{uniqueId} AND company_id = #{companyId} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByUniqueId(@Param("uniqueId") String uniqueId, @Param("companyId") Long companyId,
                        @Param("excludeId") Long excludeId);

    /**
     * 统计同一唯一标识编号截至某日期已出现的次数（用于计算"第几次"）。
     * selfId 非空（编辑场景）时按"日期更早或同日且 id 更小"统计，同日多条按 id 稳定排序，互不干扰；
     * selfId 为空（新增/导入场景）时按"日期 &lt;= 当天"统计，同日已有记录视为更早（追加语义）。
     */
    @Select("<script>SELECT COUNT(*) FROM unwarranted_material " +
            "WHERE unique_id = #{uniqueId} AND company_id = #{companyId} " +
            "<choose>" +
            "<when test='selfId != null'>AND (record_date &lt; #{date} OR (record_date = #{date} AND id &lt; #{selfId}))</when>" +
            "<otherwise>AND record_date &lt;= #{date}</otherwise>" +
            "</choose>" +
            "</script>")
    int countByUniqueIdBefore(@Param("uniqueId") String uniqueId, @Param("companyId") Long companyId,
                              @Param("date") LocalDate date, @Param("selfId") Long selfId);

    /**
     * 查询同一唯一标识编号截至某日期最近一条记录（按日期倒序），用于"上次日期/上次维修人"。
     * 排序规则同 countByUniqueIdBefore（编辑场景同日按 id 稳定排序）。
     */
    @Select("<script>SELECT * FROM unwarranted_material " +
            "WHERE unique_id = #{uniqueId} AND company_id = #{companyId} " +
            "<choose>" +
            "<when test='selfId != null'>AND (record_date &lt; #{date} OR (record_date = #{date} AND id &lt; #{selfId}))</when>" +
            "<otherwise>AND record_date &lt;= #{date}</otherwise>" +
            "</choose> " +
            "ORDER BY record_date DESC, id DESC LIMIT 1</script>")
    UnwarrantedMaterial findLatestByUniqueIdBefore(@Param("uniqueId") String uniqueId, @Param("companyId") Long companyId,
                                                   @Param("date") LocalDate date, @Param("selfId") Long selfId);

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

    /** 统计同一维修记录已被关联的次数（公司内，可排除当前记录，用于校验一个维修记录只允许关联一条未过保物料） */
    @Select("<script>SELECT COUNT(*) FROM unwarranted_material WHERE original_record_id = #{originalRecordId} " +
            "AND company_id = #{companyId} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByOriginalRecordId(@Param("originalRecordId") Long originalRecordId,
                                @Param("companyId") Long companyId,
                                @Param("excludeId") Long excludeId);

    /** 查询某维修记录关联的全部未过保物料（编辑维修记录时同步更新用） */
    @Select("SELECT * FROM unwarranted_material WHERE original_record_id = #{originalRecordId} AND company_id = #{companyId} ORDER BY id")
    List<UnwarrantedMaterial> findByOriginalRecordId(@Param("originalRecordId") Long originalRecordId,
                                                     @Param("companyId") Long companyId);

    /** 删除某维修记录关联的全部未过保物料（删除维修记录时级联删除用） */
    @Delete("DELETE FROM unwarranted_material WHERE original_record_id = #{originalRecordId} AND company_id = #{companyId}")
    int deleteByOriginalRecordId(@Param("originalRecordId") Long originalRecordId,
                                 @Param("companyId") Long companyId);

    /** 批量统计多条维修记录的关联未过保物料总数（前端批量删除提示用，companyId 可空） */
    @Select("<script>SELECT COALESCE(SUM(cnt), 0) AS total FROM (" +
            "SELECT original_record_id, COUNT(*) AS cnt FROM unwarranted_material " +
            "WHERE original_record_id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach> " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "GROUP BY original_record_id) t</script>")
    int countByOriginalRecordIds(@Param("ids") List<Long> ids, @Param("companyId") Long companyId);

    /**
     * 超比统计"当月返修"：按料号+月份统计未过保物料中 warranty_status='未过保' 的记录数量之和。
     * （口径：与 Excel 原表一致，从未过保物料表取数，而非维修记录表）
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM unwarranted_material " +
            "WHERE material_code = #{materialCode} AND DATE_FORMAT(record_date, '%Y-%m') = #{month} " +
            "AND warranty_status = '未过保' AND company_id = #{companyId}")
    int countRepairByMaterialCodeAndMonth(@Param("materialCode") String materialCode,
                                          @Param("month") String month,
                                          @Param("companyId") Long companyId);
}
