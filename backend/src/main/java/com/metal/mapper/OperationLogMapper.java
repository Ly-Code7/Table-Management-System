package com.metal.mapper;

import com.metal.entity.OperationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OperationLogMapper {

    @Insert("INSERT INTO operation_log (user_id, username, action, table_name, record_id, detail, ip, company_id) " +
            "VALUES (#{userId}, #{username}, #{action}, #{tableName}, #{recordId}, #{detail}, #{ip}, #{companyId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OperationLog log);

    @Select("SELECT * FROM operation_log ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<OperationLog> findAll(int offset, int limit);

    @Select("SELECT COUNT(*) FROM operation_log")
    long count();

    @Select("<script>" +
            "SELECT * FROM operation_log WHERE 1=1 " +
            "<if test='userId != null'>AND user_id = #{userId}</if> " +
            "<if test='tableName != null and tableName != \"\"'>AND table_name = #{tableName}</if> " +
            "<if test='action != null and action != \"\"'>AND action = #{action}</if> " +
            "<if test='startDate != null'>AND created_at &gt;= #{startDate}</if> " +
            "<if test='endDate != null'>AND created_at &lt;= #{endDate}</if> " +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<OperationLog> search(@Param("userId") Long userId, @Param("tableName") String tableName,
                              @Param("action") String action,
                              @Param("startDate") String startDate, @Param("endDate") String endDate,
                              @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM operation_log WHERE 1=1 " +
            "<if test='userId != null'>AND user_id = #{userId}</if> " +
            "<if test='tableName != null and tableName != \"\"'>AND table_name = #{tableName}</if> " +
            "<if test='action != null and action != \"\"'>AND action = #{action}</if> " +
            "<if test='startDate != null'>AND created_at &gt;= #{startDate}</if> " +
            "<if test='endDate != null'>AND created_at &lt;= #{endDate}</if> " +
            "</script>")
    long searchCount(@Param("userId") Long userId, @Param("tableName") String tableName,
                     @Param("action") String action,
                     @Param("startDate") String startDate, @Param("endDate") String endDate);
}
