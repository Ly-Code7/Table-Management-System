package com.metal.mapper;

import com.metal.entity.DeliveryRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DeliveryRecordMapper {

    @Select("SELECT * FROM delivery_record ORDER BY id DESC")
    List<DeliveryRecord> findAll();

    @Select("SELECT * FROM delivery_record WHERE id = #{id}")
    DeliveryRecord findById(Long id);

    @Insert("INSERT INTO delivery_record (company_id, record_date, category, material_name, spec_model, material_code, " +
            "material_serial, quantity, unit, brand, product_attr, factory, shipment_no, remark, `year_month`, created_by, updated_by) " +
            "VALUES (#{companyId}, #{recordDate}, #{category}, #{materialName}, #{specModel}, #{materialCode}, " +
            "#{materialSerial}, #{quantity}, #{unit}, #{brand}, #{productAttr}, #{factory}, #{shipmentNo}, " +
            "#{remark}, #{yearMonth}, #{createdBy}, #{updatedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeliveryRecord record);

    @Update("UPDATE delivery_record SET record_date=#{recordDate}, category=#{category}, material_name=#{materialName}, " +
            "spec_model=#{specModel}, material_code=#{materialCode}, material_serial=#{materialSerial}, " +
            "quantity=#{quantity}, unit=#{unit}, brand=#{brand}, product_attr=#{productAttr}, factory=#{factory}, " +
            "shipment_no=#{shipmentNo}, remark=#{remark}, `year_month`=#{yearMonth}, updated_by=#{updatedBy} " +
            "WHERE id=#{id}")
    int update(DeliveryRecord record);

    @Delete("DELETE FROM delivery_record WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("<script>DELETE FROM delivery_record WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    int batchDelete(@Param("ids") List<Long> ids);

    @Select("<script>" +
            "SELECT * FROM delivery_record WHERE 1=1 " +
            "<if test='companyId != null'>AND company_id = #{companyId}</if> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (category LIKE CONCAT('%',#{keyword},'%') OR material_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR spec_model LIKE CONCAT('%',#{keyword},'%') OR material_code LIKE CONCAT('%',#{keyword},'%') " +
            "OR material_serial LIKE CONCAT('%',#{keyword},'%') OR brand LIKE CONCAT('%',#{keyword},'%') " +
            "OR factory LIKE CONCAT('%',#{keyword},'%') OR shipment_no LIKE CONCAT('%',#{keyword},'%') " +
            "OR unit LIKE CONCAT('%',#{keyword},'%') OR product_attr LIKE CONCAT('%',#{keyword},'%') " +
            "OR remark LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='category != null and category != \"\"'>AND category = #{category}</if> " +
            "<if test='productAttr != null and productAttr != \"\"'>AND product_attr = #{productAttr}</if> " +
            "<if test='factory != null and factory != \"\"'>AND factory = #{factory}</if> " +
            "<if test='startDate != null'>AND record_date &gt;= #{startDate}</if> " +
            "<if test='endDate != null'>AND record_date &lt;= #{endDate}</if> " +
            "ORDER BY ${sortField} ${sortOrder} " +
            "</script>")
    List<DeliveryRecord> search(@Param("companyId") Long companyId,
                                @Param("keyword") String keyword,
                                @Param("category") String category,
                                @Param("productAttr") String productAttr,
                                @Param("factory") String factory,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate,
                                @Param("sortField") String sortField,
                                @Param("sortOrder") String sortOrder);

    @Select("SELECT DISTINCT material_code, category, material_name, spec_model FROM delivery_record")
    List<DeliveryRecord> findDistinctMaterials();

    /** 批量插入（每批最多 500 条，提升大数据量导入性能） */
    @Insert("<script>" +
            "INSERT INTO delivery_record (company_id, record_date, category, material_name, spec_model, material_code, " +
            "material_serial, quantity, unit, brand, product_attr, factory, shipment_no, remark, `year_month`, created_by, updated_by) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.companyId}, #{r.recordDate}, #{r.category}, #{r.materialName}, #{r.specModel}, #{r.materialCode}, " +
            "#{r.materialSerial}, #{r.quantity}, #{r.unit}, #{r.brand}, #{r.productAttr}, #{r.factory}, #{r.shipmentNo}, " +
            "#{r.remark}, #{r.yearMonth}, #{r.createdBy}, #{r.updatedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<DeliveryRecord> records);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM delivery_record WHERE material_code = #{materialCode} " +
            "AND DATE_FORMAT(record_date, '%Y-%m') = #{month} AND company_id = #{companyId}")
    int countByMaterialCodeAndMonth(@Param("materialCode") String materialCode, @Param("month") String month,
                                    @Param("companyId") Long companyId);

    @Select("SELECT DAY(record_date) as day, COALESCE(SUM(quantity), 0) as cnt FROM delivery_record " +
            "WHERE material_code = #{materialCode} AND DATE_FORMAT(record_date, '%Y-%m') = #{month} " +
            "AND company_id = #{companyId} " +
            "GROUP BY DAY(record_date) ORDER BY day")
    List<java.util.Map<String, Object>> countDailyByMaterialCodeAndMonth(
            @Param("materialCode") String materialCode, @Param("month") String month,
            @Param("companyId") Long companyId);

    @Select("SELECT COUNT(*) FROM delivery_record WHERE material_serial = #{serial} AND DATE_FORMAT(record_date, '%Y-%m') = #{month} " +
            "AND (#{companyId} IS NULL OR company_id = #{companyId})")
    int countByMaterialSerialAndMonth(@Param("serial") String serial, @Param("month") String month,
                                      @Param("companyId") Long companyId);

    /** 根据上机物料号（序列号或物料名称）查找送货记录，用于维修记录回填料号 */
    @Select("SELECT * FROM delivery_record WHERE material_serial = #{keyword} OR material_name = #{keyword} ORDER BY id DESC LIMIT 1")
    DeliveryRecord findByMaterialSerial(@Param("keyword") String keyword);

    /** 根据物料编码查找最近的送货记录，用于自动回填 */
    @Select("SELECT * FROM delivery_record WHERE material_code = #{materialCode} ORDER BY id DESC LIMIT 1")
    DeliveryRecord findLatestByMaterialCode(@Param("materialCode") String materialCode);
}
