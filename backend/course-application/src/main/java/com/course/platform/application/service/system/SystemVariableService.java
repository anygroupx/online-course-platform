package com.course.platform.application.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.dto.SystemVariableCreateRequest;
import com.course.platform.domain.dto.SystemVariableUpdateRequest;
import com.course.platform.domain.entity.SystemVariable;

import java.util.List;
import java.util.Map;

/**
 * 系统变量管理服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于系统变量管理需求设计
 */
public interface SystemVariableService {

    /**
     * 创建系统变量
     * 
     * @param request 创建请求
     * @param operatorId 操作人ID
     * @return 变量ID
     */
    Long createVariable(SystemVariableCreateRequest request, Long operatorId);

    /**
     * 更新系统变量
     * 
     * @param request 更新请求
     * @param operatorId 操作人ID
     */
    void updateVariable(SystemVariableUpdateRequest request, Long operatorId);

    /**
     * 原子批量更新主题变量，避免多次请求造成部分发布。
     */
    void updateThemeVariables(List<SystemVariableUpdateRequest> requests, Long operatorId);

    /**
     * 删除系统变量
     * 
     * @param variableId 变量ID
     * @param operatorId 操作人ID
     */
    void deleteVariable(Long variableId, Long operatorId);

    /**
     * 根据类型查询变量列表
     * 
     * @param variableType 变量类型
     * @return 变量列表
     */
    List<SystemVariable> getVariablesByType(String variableType);

    /**
     * 分页查询变量
     * 
     * @param variableType 变量类型（可选）
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 变量分页数据
     */
    IPage<SystemVariable> queryVariables(String variableType, String keyword, Integer page, Integer pageSize);

    /**
     * 获取变量详情
     * 
     * @param variableId 变量ID
     * @return 变量详情
     */
    SystemVariable getVariableById(Long variableId);

    /**
     * 获取面向客户端的已启用主题颜色配置。
     *
     * @return light/dark -> 变量键 -> 色值
     */
    Map<String, Map<String, String>> getEnabledThemeVariables();

    /**
     * 启用/禁用变量
     * 
     * @param variableId 变量ID
     * @param enabled 是否启用
     * @param operatorId 操作人ID
     */
    void toggleVariableStatus(Long variableId, Boolean enabled, Long operatorId);

    /**
     * 设置默认变量
     * 
     * @param variableId 变量ID
     * @param operatorId 操作人ID
     */
    void setDefaultVariable(Long variableId, Long operatorId);
}
