package com.metal.common;

import com.metal.interceptor.AuthInterceptor;

/**
 * Service 层通用工具方法，消除各 Service 中的重复代码
 */
public final class ServiceHelper {

    private ServiceHelper() {
        // 工具类，禁止实例化
    }

    /**
     * SQL ORDER BY 字段安全过滤，防止 SQL 注入
     * 只允许字母、数字、下划线组成的字段名
     *
     * @param field        前端传入的排序字段
     * @param defaultField 兜底默认字段
     * @return 安全的字段名
     */
    public static String sanitizeSortField(String field, String defaultField) {
        if (field == null || field.isBlank()) {
            return defaultField;
        }
        if (!field.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return defaultField;
        }
        return field;
    }

    /**
     * 安全的排序方向校验
     *
     * @param sortOrder 前端传入的排序方向 (asc/desc)
     * @return 安全的排序方向
     */
    public static String sanitizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }

    /**
     * 获取当前登录用户的真实姓名
     * 未登录时返回 "系统"
     *
     * @return 当前用户姓名
     */
    public static String getCurrentUserName() {
        AuthInterceptor.UserContext ctx = AuthInterceptor.getCurrentUser();
        return ctx != null ? ctx.getRealName() : "系统";
    }

    /**
     * 获取当前登录用户的角色
     *
     * @return 当前用户角色，未登录时返回 "user"
     */
    public static String getCurrentUserRole() {
        AuthInterceptor.UserContext ctx = AuthInterceptor.getCurrentUser();
        return ctx != null ? ctx.getRole() : "user";
    }

    /**
     * 拼接 厂房+机台号（如 "B5-H1"），任一字段为空时返回 null。
     * 供机台详情、未过保物料等多处共用，保证拼接规则一致。
     */
    public static String combineFactoryMachine(String factory, String machineNo) {
        if (factory == null || machineNo == null || factory.isBlank() || machineNo.isBlank()) {
            return null;
        }
        return factory + "-" + machineNo;
    }

    /**
     * 判断当前用户是否为管理员
     *
     * @return true 如果是 admin
     */
    public static boolean isAdmin() {
        return "admin".equals(getCurrentUserRole());
    }

    /**
     * Check if current user is admin or the record's creator.
     * Throws BizException if not authorized to modify.
     * If the record has null createdBy (historical data), allow the operation.
     */
    public static void checkOwnershipOrAdmin(String recordCreatedBy, String action) {
        if (isAdmin()) return;
        if (recordCreatedBy == null) return;
        String currentUser = getCurrentUserName();
        if (!currentUser.equals(recordCreatedBy)) {
            throw new BizException("无权限" + action + "：只能操作自己创建的数据");
        }
    }

    /**
     * Check if current user is admin, throw if not
     */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BizException("无权限：仅管理员可操作");
        }
    }
}
