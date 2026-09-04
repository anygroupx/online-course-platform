package com.course.platform.application.service.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.dto.RechargeRequest;
import com.course.platform.domain.dto.UserCreateRequest;
import com.course.platform.domain.dto.UserUpdateRequest;
import com.course.platform.domain.entity.User;

/**
 * 用户服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface UserService {

    /**
     * 创建用户（开户）
     * 
     * @param request 创建请求
     * @param operatorId 操作人ID
     * @return 对外用户 UUID
     */
    String createUser(UserCreateRequest request, Long operatorId);

    /**
     * 更新用户信息
     * 
     * @param request 更新请求
     * @param operatorId 操作人ID
     */
    void updateUser(UserUpdateRequest request, Long operatorId);

    /**
     * 分页查询用户列表
     * 
     * @param keyword 搜索关键词
     * @param status 状态
     * @param page 当前页
     * @param pageSize 每页数量
     * @param operatorId 操作人ID
     * @return 用户分页数据
     */
    IPage<User> queryUsers(String keyword, Integer status, Integer page, Integer pageSize, Long operatorId);

    /**
     * 获取用户详情
     * 
     * @param userUid 对外用户 UUID
     * @param operatorId 操作人ID
     * @return 用户信息
     */
    User getUserByUid(String userUid, Long operatorId);

    /**
     * 充值
     * 
     * @param request 充值请求
     * @param operatorId 操作人ID（充值人）
     */
    void recharge(RechargeRequest request, Long operatorId);

    /**
     * 修改密码
     * 
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 重置密码
     * 
     * @param targetUserUid 目标用户 UUID
     * @param operatorId 操作人ID
     * @return 新密码
     */
    String resetPassword(String targetUserUid, Long operatorId);

    /**
     * 禁用/启用用户
     * 
     * @param userId 用户ID
     * @param status 状态
     * @param operatorId 操作人ID
     */
    void changeUserStatus(String userUid, Integer status, Long operatorId);
}

