package com.metal.mapper;

import com.metal.entity.OriginalRecord;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface OriginalRecordMapper {

    @Select("SELECT * FROM original_record WHERE id = #{id}")
    OriginalRecord findById(Long id);

    @Insert("INSERT INTO original_record (company_id, `year_month`, record_date, shift, factory, serial_number, machine_no, " +
            "diagnostician, repair_person, repair_request_time, start_time, end_time, repair_hours, downtime_hours, " +
            "machine_model, fault_phenomenon, fault_description, material_code, part_name, quantity, " +
            "machine_on_material, machine_off_material, remark, confirmer, delivery_record_ref, document_no, " +
            "last_machine_on_time, is_out_of_warranty, created_by, updated_by) " +
            "VALUES (#{companyId}, #{yearMonth}, #{recordDate}, #{shift}, #{factory}, #{serialNumber}, #{machineNo}, " +
            "#{diagnostician}, #{repairPerson}, #{repairRequestTime}, #{startTime}, #{endTime}, " +
            "#{repairHours}, #{downtimeHours}, #{machineModel}, #{faultPhenomenon}, #{faultDescription}, " +
            "#{materialCode}, #{partName}, #{quantity}, #{machineOnMaterial}, #{machineOffMaterial}, " +
            "#{remark}, #{confirmer}, #{deliveryRecordRef}, #{documentNo}, #{lastMachineOnTime}, #{isOutOfWarranty}, " +
            "#{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OriginalRecord record);

    @Update("UPDATE original_record SET `year_month`=#{yearMonth}, record_date=#{recordDate}, shift=#{shift}, " +
            "factory=#{factory}, serial_number=#{serialNumber}, machine_no=#{machineNo}, " +
            "diagnostician=#{diagnostician}, repair_person=#{repairPerson}, repair_request_time=#{repairRequestTime}, " +
            "start_time=#{startTime}, end_time=#{endTime}, repair_hours=#{repairHours}, downtime_hours=#{downtimeHours}, " +
            "machine_model=#{machineModel}, fault_phenomenon=#{faultPhenomenon}, fault_description=#{faultDescription}, " +
            "material_code=#{materialCode}, part_name=#{partName}, quantity=#{quantity}, " +
            "machine_on_material=#{machineOnMaterial}, machine_off_material=#{machineOffMaterial}, " +
            "remark=#{remark}, confirmer=#{confirmer}, delivery_record_ref=#{deliveryRecordRef}, document_no=#{documentNo}, " +
            "last_machine_on_time=#{lastMachineOnTime}, is_out_of_warranty=#{isOutOfWarranty}, updated_by=#{updatedBy} " +
            "WHERE id=#{id}")
    int update(OriginalRecord record);

    @Delete("DELETE FROM original_record WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM original_record WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    /** 批量插入（每批最多 500 条，提升大数据量导入性能） */
    @Insert("<script>" +
            "INSERT INTO original_record (company_id, `year_month`, record_date, shift, factory, serial_number, machine_no, " +
            "diagnostician, repair_person, repair_request_time, start_time, end_time, repair_hours, downtime_hours, " +
            "machine_model, fault_phenomenon, fault_description, material_code, part_name, quantity, " +
            "machine_on_material, machine_off_material, remark, confirmer, delivery_record_ref, document_no, " +
            "last_machine_on_time, is_out_of_warranty, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.yearMonth}, #{r.recordDate}, #{r.shift}, #{r.factory}, #{r.serialNumber}, #{r.machineNo}, " +
            "#{r.diagnostician}, #{r.repairPerson}, #{r.repairRequestTime}, #{r.startTime}, #{r.endTime}, " +
            "#{r.repairHours}, #{r.downtimeHours}, #{r.machineModel}, #{r.faultPhenomenon}, #{r.faultDescription}, " +
            "#{r.materialCode}, #{r.partName}, #{r.quantity}, #{r.machineOnMaterial}, #{r.machineOffMaterial}, " +
            "#{r.remark}, #{r.confirmer}, #{r.deliveryRecordRef}, #{r.documentNo}, #{r.lastMachineOnTime}, #{r.isOutOfWarranty}, " +
            "#{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<OriginalRecord> records);

    /**
     * 查询某个下机物料的最后一次上机时间
     */
    @Select("SELECT MAX(record_date) FROM original_record WHERE machine_on_material = #{machineOnMaterial}")
    LocalDate findLastMachineOnTime(@Param("machineOnMaterial") String machineOnMaterial);

    @Select("<script>" +
            "SELECT * FROM original_record WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (serial_number LIKE CONCAT('%',#{keyword},'%') OR machine_no LIKE CONCAT('%',#{keyword},'%') " +
            "OR material_code LIKE CONCAT('%',#{keyword},'%') OR machine_model LIKE CONCAT('%',#{keyword},'%') " +
            "OR diagnostician LIKE CONCAT('%',#{keyword},'%') OR repair_person LIKE CONCAT('%',#{keyword},'%') " +
            "OR confirmer LIKE CONCAT('%',#{keyword},'%') OR factory LIKE CONCAT('%',#{keyword},'%') " +
            "OR fault_phenomenon LIKE CONCAT('%',#{keyword},'%') OR fault_description LIKE CONCAT('%',#{keyword},'%') " +
            "OR part_name LIKE CONCAT('%',#{keyword},'%') OR remark LIKE CONCAT('%',#{keyword},'%') " +
            "OR document_no LIKE CONCAT('%',#{keyword},'%') " +
            "OR machine_on_material LIKE CONCAT('%',#{keyword},'%') " +
            "OR machine_off_material LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='shift != null and shift != \"\"'>AND shift = #{shift}</if> " +
            "<if test='factory != null and factory != \"\"'>AND factory = #{factory}</if> " +
            "<if test='isOutOfWarranty != null and isOutOfWarranty != \"\"'>AND is_out_of_warranty = #{isOutOfWarranty}</if> " +
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
                                @Param("isOutOfWarranty") String isOutOfWarranty,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate,
                                @Param("sortField") String sortField,
                                @Param("sortOrder") String sortOrder,
                                @Param("excludeLinked") Boolean excludeLinked);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM original_record WHERE material_code = #{materialCode} " +
            "AND DATE_FORMAT(record_date, '%Y-%m') = #{month} AND company_id = #{companyId}")
    int countByMaterialCodeAndMonth(@Param("materialCode") String materialCode, @Param("month") String month,
                                    @Param("companyId") Long companyId);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM original_record WHERE material_code = #{materialCode} " +
            "AND DATE_FORMAT(record_date, '%Y-%m') = #{month} AND is_out_of_warranty = '未过保' AND company_id = #{companyId}")
    int countRepairByMaterialCodeAndMonth(@Param("materialCode") String materialCode, @Param("month") String month,
                                          @Param("companyId") Long companyId);
}
