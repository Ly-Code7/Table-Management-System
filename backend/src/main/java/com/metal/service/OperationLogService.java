package com.metal.service;

import com.metal.common.PageResult;
import com.metal.entity.OperationLog;
import com.metal.interceptor.AuthInterceptor;
import com.metal.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    public void save(OperationLog log) {
        operationLogMapper.insert(log);
    }

    /**
     * 通用操作日志入口：记录用户直接写操作（INSERT/UPDATE/DELETE）。
     * 从登录上下文取 userId/username；不记录 IP（用户明确不需要）；
     * recordId 为被操作记录的主键 id（批量按月份删除等无单条主键场景传 null，由 detail 补偿说明）。
     *
     * @param action    INSERT / UPDATE / DELETE
     * @param tableName 业务表名（如 original_record）
     * @param recordId  被操作记录主键 id
     * @param companyId 被操作记录所属公司 id（用户管理无公司归属时传 null）
     * @param detail    操作详情（DELETE 传 null，与既有行为一致）
     */
    public void log(String action, String tableName, Long recordId, Long companyId, String detail) {
        OperationLog log = new OperationLog();
        var ctx = AuthInterceptor.getCurrentUser();
        if (ctx != null) {
            log.setUserId(ctx.getUserId());
            log.setUsername(ctx.getUsername());
        }
        log.setAction(action);
        log.setTableName(tableName);
        log.setRecordId(recordId);
        log.setCompanyId(companyId);
        log.setDetail(detail);
        save(log);
    }

    public PageResult<OperationLog> query(int page, int pageSize, Long userId,
                                           String tableName, String action,
                                           String startDate, String endDate) {
        int offset = (page - 1) * pageSize;
        var list = operationLogMapper.search(userId, tableName, action, startDate, endDate, offset, pageSize);
        long total = operationLogMapper.searchCount(userId, tableName, action, startDate, endDate);
        return new PageResult<>(total, page, pageSize, list);
    }
}
