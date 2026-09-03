package com.metal.mapper;

import com.metal.entity.OriginalRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface OriginalRecordMapper {

    @Select("SELECT * FROM original_record WHERE id = #{id}")
    OriginalRecord findById(Long id);

    /** 公司内按 id 查询，用于跨表回填时防止读取其他公司的维修记录 */
    @Select("SELECT * FROM original_record WHERE id = #{id} AND company_id = #{companyId}")
    OriginalRecord findByIdAndCompany(@Param("id") Long id, @Param("companyId") Long companyId);

    @Insert("INSERT INTO original_record (company_id, `year_month`, record_date, shift, factory, serial_number, machine_no, plant_machine, " +
            "diagnostician, repair_person, repair_request_time, start_time, end_time, repair_hours, downtime_hours, " +
            "machine_model, fault_phenomenon, fault_description, material_code, material_156_name, part_name, quantity, " +
            "machine_on_material, machine_off_material, machine_off_code, remark, confirmer, delivery_record_ref, document_no, " +
            "image_key, machine_on_customer, machine_off_customer, created_by, updated_by) " +
            "VALUES (#{companyId}, #{yearMonth}, #{recordDate}, #{shift}, #{factory}, #{serialNumber}, #{machineNo}, #{plantMachine}, " +
            "#{diagnostician}, #{repairPerson}, #{repairRequestTime}, #{startTime}, #{endTime}, " +
            "#{repairHours}, #{downtimeHours}, #{machineModel}, #{faultPhenomenon}, #{faultDescription}, " +
            "#{materialCode}, #{material156Name}, #{partName}, #{quantity}, #{machineOnMaterial}, #{machineOffMaterial}, " +
            "#{machineOffCode}, #{remark}, #{confirmer}, #{deliveryRecordRef}, #{documentNo}, " +
            "#{imageKey}, #{machineOnCustomer}, #{machineOffCustomer}, #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OriginalRecord record);

    @Update("UPDATE original_record SET `year_month`=#{yearMonth}, record_date=#{recordDate}, shift=#{shift}, " +
            "factory=#{factory}, serial_number=#{serialNumber}, machine_no=#{machineNo}, plant_machine=#{plantMachine}, " +
            "diagnostician=#{diagnostician}, repair_person=#{repairPerson}, repair_request_time=#{repairRequestTime}, " +
            "start_time=#{startTime}, end_time=#{endTime}, repair_hours=#{repairHours}, downtime_hours=#{downtimeHours}, " +
            "machine_model=#{machineModel}, fault_phenomenon=#{faultPhenomenon}, fault_description=#{faultDescription}, " +
            "material_code=#{materialCode}, material_156_name=#{material156Name}, part_name=#{partName}, quantity=#{quantity}, " +
            "machine_on_material=#{machineOnMaterial}, machine_off_material=#{machineOffMaterial}, machine_off_code=#{machineOffCode}, " +
            "remark=#{remark}, confirmer=#{confirmer}, delivery_record_ref=#{deliveryRecordRef}, document_no=#{documentNo}, " +
            "image_key=#{imageKey}, machine_on_customer=#{machineOnCustomer}, machine_off_customer=#{machineOffCustomer}, updated_by=#{updatedBy} " +
            "WHERE id=#{id}")
    int update(OriginalRecord record);

    @Delete("DELETE FROM original_record WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM original_record WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    /** 批量插入（每批最多 500 条，提升大数据量导入性能） */
    @Insert("<script>" +
            "INSERT INTO original_record (company_id, `year_month`, record_date, shift, factory, serial_number, machine_no, plant_machine, " +
            "diagnostician, repair_person, repair_request_time, start_time, end_time, repair_hours, downtime_hours, " +
            "machine_model, fault_phenomenon, fault_description, material_code, material_156_name, part_name, quantity, " +
            "machine_on_material, machine_off_material, machine_off_code, remark, confirmer, delivery_record_ref, document_no, " +
            "image_key, machine_on_customer, machine_off_customer, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.yearMonth}, #{r.recordDate}, #{r.shift}, #{r.factory}, #{r.serialNumber}, #{r.machineNo}, #{r.plantMachine}, " +
            "#{r.diagnostician}, #{r.repairPerson}, #{r.repairRequestTime}, #{r.startTime}, #{r.endTime}, " +
            "#{r.repairHours}, #{r.downtimeHours}, #{r.machineModel}, #{r.faultPhenomenon}, #{r.faultDescription}, " +
            "#{r.materialCode}, #{r.material156Name}, #{r.partName}, #{r.quantity}, #{r.machineOnMaterial}, #{r.machineOffMaterial}, " +
            "#{r.machineOffCode}, #{r.remark}, #{r.confirmer}, #{r.deliveryRecordRef}, #{r.documentNo}, " +
            "#{r.imageKey}, #{r.machineOnCustomer}, #{r.machineOffCustomer}, #{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int batchInsert(@Param("list") List<OriginalRecord> records);

    @Select("<script>" +
            "SELECT * FROM original_record WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            // 关键词匹配集：上机/下机"是否客户物料"列值仅存 是/否——值 LIKE 覆盖"是/否"，含"客户"字样时命中标记为"是"的行
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (serial_number LIKE CONCAT('%',#{keyword},'%') OR machine_no LIKE CONCAT('%',#{keyword},'%') " +
            "OR material_code LIKE CONCAT('%',#{keyword},'%') OR machine_model LIKE CONCAT('%',#{keyword},'%') " +
            "OR diagnostician LIKE CONCAT('%',#{keyword},'%') OR repair_person LIKE CONCAT('%',#{keyword},'%') " +
            "OR confirmer LIKE CONCAT('%',#{keyword},'%') OR factory LIKE CONCAT('%',#{keyword},'%') " +
            "OR fault_phenomenon LIKE CONCAT('%',#{keyword},'%') OR fault_description LIKE CONCAT('%',#{keyword},'%') " +
            "OR part_name LIKE CONCAT('%',#{keyword},'%') OR remark LIKE CONCAT('%',#{keyword},'%') " +
            "OR plant_machine LIKE CONCAT('%',#{keyword},'%') " +
            "OR document_no LIKE CONCAT('%',#{keyword},'%') " +
            "OR machine_on_material LIKE CONCAT('%',#{keyword},'%') " +
            "OR machine_off_material LIKE CONCAT('%',#{keyword},'%') " +
            "OR machine_on_customer LIKE CONCAT('%',#{keyword},'%') " +
            "OR machine_off_customer LIKE CONCAT('%',#{keyword},'%') " +
            "OR (machine_on_customer = '是' AND #{keyword} LIKE '%客户%') " +
            "OR (machine_off_customer = '是' AND #{keyword} LIKE '%客户%') OR id LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='shift != null and shift != \"\"'>AND shift = #{shift}</if> " +
            "<if test='factory != null and factory != \"\"'>AND factory = #{factory}</if> " +
            "<if test='startDate != null'>AND record_date &gt;= #{startDate}</if> " +
            "<if test='endDate != null'>AND record_date &lt;= #{endDate}</if> " +
            "<if test='excludeLinked != null and excludeLinked'>" +
            "AND id NOT IN (SELECT original_record_id FROM unwarranted_material WHERE original_record_id IS NOT NULL " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if>) " +
            "</if>" +
            "ORDER BY ${sortField} ${sortOrder} " +
            "</script>")
    List<OriginalRecord> search(@Param("companyId") Long companyId, @Param("keyword") String keyword,
                                @Param("shift") String shift,
                                @Param("factory") String factory,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate,
                                @Param("sortField") String sortField,
                                @Param("sortOrder") String sortOrder,
                                @Param("excludeLinked") Boolean excludeLinked);

    /** 上机数量（月度，超比统计用）：排除"上机是否客户物料 = 是"的记录（客户物料不纳入超比统计） */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM original_record WHERE material_code = #{materialCode} " +
            "AND DATE_FORMAT(record_date, '%Y-%m') = #{month} AND company_id = #{companyId} " +
            "AND (machine_on_customer IS NULL OR machine_on_customer <> '是')")
    int countByMaterialCodeAndMonth(@Param("materialCode") String materialCode, @Param("month") String month,
                                    @Param("companyId") Long companyId);

    /** 区间上机数量：record_date 在 [startDate, endDate] 内（含端点）的上机数量合计（超比统计按日期区间实时统计），排除"上机是否客户物料 = 是"的记录 */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM original_record WHERE material_code = #{materialCode} " +
            "AND record_date >= #{startDate} AND record_date <= #{endDate} AND company_id = #{companyId} " +
            "AND (machine_on_customer IS NULL OR machine_on_customer <> '是')")
    int countByMaterialCodeAndDateRange(@Param("materialCode") String materialCode,
                                        @Param("startDate") String startDate,
                                        @Param("endDate") String endDate,
                                        @Param("companyId") Long companyId);

}
