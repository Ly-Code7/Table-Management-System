package com.metal.service;

import com.metal.common.BizException;
import com.metal.common.ServiceHelper;
import com.metal.entity.SysUser;
import com.metal.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserManageService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OperationLogService logService;

    public List<SysUser> listAll() {
        ServiceHelper.requireAdmin();
        return sysUserMapper.findAll();
    }

    @Transactional
    public void updateRole(Long userId, String role) {
        ServiceHelper.requireAdmin();
        SysUser user = sysUserMapper.findById(userId);
        if (user == null) throw new BizException("用户不存在");
        if (!"admin".equals(role) && !"user".equals(role)) {
            throw new BizException("无效的角色");
        }
        sysUserMapper.updateRole(userId, role);
        logService.log("UPDATE", "sys_user", userId, null, "角色变更: " + user.getRole() + " -> " + role);
    }

    @Transactional
    public void deleteUser(Long userId) {
        ServiceHelper.requireAdmin();
        SysUser user = sysUserMapper.findById(userId);
        if (user == null) throw new BizException("用户不存在");
        if ("admin".equals(user.getRole())) {
            // Check if this is the last admin
            List<SysUser> admins = sysUserMapper.findByRole("admin");
            if (admins.size() <= 1) {
                throw new BizException("不能删除最后一个管理员");
            }
        }
        sysUserMapper.deleteById(userId);
        logService.log("DELETE", "sys_user", userId, null, "删除用户: " + user.getRealName() + " (" + user.getUsername() + ")");
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        ServiceHelper.requireAdmin();
        SysUser user = sysUserMapper.findById(userId);
        if (user == null) throw new BizException("用户不存在");
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("密码至少6位");
        }
        sysUserMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        logService.log("UPDATE", "sys_user", userId, null, "重置密码: " + user.getRealName() + " (" + user.getUsername() + ")");
    }
}
