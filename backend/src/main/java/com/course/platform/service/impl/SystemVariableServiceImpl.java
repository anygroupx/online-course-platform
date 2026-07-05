package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.shared.constant.Constants;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.ResultCode;
import com.course.platform.domain.dto.SystemVariableCreateRequest;
import com.course.platform.domain.dto.SystemVariableUpdateRequest;
import com.course.platform.domain.entity.SystemVariable;
import com.course.platform.mapper.SystemVariableMapper;
import com.course.platform.service.OperationLogService;
import com.course.platform.service.SystemVariableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 系统变量管理服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于系统变量管理需求设计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemVariableServiceImpl implements SystemVariableService {

    private final SystemVariableMapper systemVariableMapper;
    private final OperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVariable(SystemVariableCreateRequest request, Long operatorId) {
        // 1. 检查变量键名和类型是否已存在
        SystemVariable existingVariable = systemVariableMapper.selectOne(new LambdaQueryWrapper<SystemVariable>()
                .eq(SystemVariable::getVariableKey, request.getVariableKey())
                .eq(SystemVariable::getVariableType, request.getVariableType()));

        if (existingVariable != null) {
            throw new BusinessException("该变量键名在指定类型下已存在");
        }

        // 2. 创建变量
        SystemVariable variable = new SystemVariable();
        variable.setVariableKey(request.getVariableKey());
        variable.setVariableName(request.getVariableName());
        variable.setVariableType(request.getVariableType());
        variable.setVariableValue(request.getVariableValue());
        variable.setVariableLabel(request.getVariableLabel());
        variable.setSortOrder(request.getSortOrder());
        variable.setIsDefault(request.getIsDefault());
        variable.setIsEnabled(request.getIsEnabled());
        variable.setColor(request.getColor());
        variable.setIcon(request.getIcon());

        systemVariableMapper.insert(variable);

        // 3. 记录操作日志
        operationLogService.log(operatorId, "创建系统变量", 
                String.format("创建系统变量：%s - %s", request.getVariableType(), request.getVariableName()),
                BigDecimal.ZERO, null);

        log.info("系统变量创建成功：variableId={}, operatorId={}", variable.getId(), operatorId);

        return variable.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVariable(SystemVariableUpdateRequest request, Long operatorId) {
        // 1. 查询变量是否存在
        SystemVariable variable = systemVariableMapper.selectById(request.getId());
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }

        // 2. 检查变量键名和类型是否与其他变量冲突
        SystemVariable existingVariable = systemVariableMapper.selectOne(new LambdaQueryWrapper<SystemVariable>()
                .eq(SystemVariable::getVariableKey, request.getVariableKey())
                .eq(SystemVariable::getVariableType, request.getVariableType())
                .ne(SystemVariable::getId, request.getId()));

        if (existingVariable != null) {
            throw new BusinessException("该变量键名在指定类型下已存在");
        }

        // 3. 更新变量
        variable.setVariableKey(request.getVariableKey());
        variable.setVariableName(request.getVariableName());
        variable.setVariableType(request.getVariableType());
        variable.setVariableValue(request.getVariableValue());
        variable.setVariableLabel(request.getVariableLabel());
        variable.setSortOrder(request.getSortOrder());
        variable.setIsDefault(request.getIsDefault());
        variable.setIsEnabled(request.getIsEnabled());
        variable.setColor(request.getColor());
        variable.setIcon(request.getIcon());

        systemVariableMapper.updateById(variable);

        // 4. 记录操作日志
        operationLogService.log(operatorId, "更新系统变量", 
                String.format("更新系统变量：%s - %s", request.getVariableType(), request.getVariableName()),
                BigDecimal.ZERO, null);

        log.info("系统变量更新成功：variableId={}, operatorId={}", request.getId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVariable(Long variableId, Long operatorId) {
        // 1. 查询变量是否存在
        SystemVariable variable = systemVariableMapper.selectById(variableId);
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }

        // 2. 检查是否为默认变量
        if (variable.getIsDefault() == 1) {
            throw new BusinessException("默认变量不能删除");
        }

        // 3. 删除变量
        systemVariableMapper.deleteById(variableId);

        // 4. 记录操作日志
        operationLogService.log(operatorId, "删除系统变量", 
                String.format("删除系统变量：%s - %s", variable.getVariableType(), variable.getVariableName()),
                BigDecimal.ZERO, null);

        log.info("系统变量删除成功：variableId={}, operatorId={}", variableId, operatorId);
    }

    @Override
    public List<SystemVariable> getVariablesByType(String variableType) {
        return systemVariableMapper.selectList(new LambdaQueryWrapper<SystemVariable>()
                .eq(SystemVariable::getVariableType, variableType)
                .eq(SystemVariable::getIsEnabled, 1)
                .orderByAsc(SystemVariable::getSortOrder)
                .orderByAsc(SystemVariable::getId));
    }

    @Override
    public IPage<SystemVariable> queryVariables(String variableType, Integer page, Integer pageSize) {
        Page<SystemVariable> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<SystemVariable> queryWrapper = new LambdaQueryWrapper<>();

        if (variableType != null && !variableType.trim().isEmpty()) {
            queryWrapper.eq(SystemVariable::getVariableType, variableType);
        }

        queryWrapper.orderByAsc(SystemVariable::getVariableType)
                .orderByAsc(SystemVariable::getSortOrder)
                .orderByAsc(SystemVariable::getId);

        return systemVariableMapper.selectPage(pageObj, queryWrapper);
    }

    @Override
    public SystemVariable getVariableById(Long variableId) {
        SystemVariable variable = systemVariableMapper.selectById(variableId);
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }
        return variable;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleVariableStatus(Long variableId, Boolean enabled, Long operatorId) {
        // 1. 查询变量是否存在
        SystemVariable variable = systemVariableMapper.selectById(variableId);
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }

        // 2. 更新状态
        variable.setIsEnabled(enabled ? 1 : 0);
        systemVariableMapper.updateById(variable);

        // 3. 记录操作日志
        operationLogService.log(operatorId, enabled ? "启用系统变量" : "禁用系统变量", 
                String.format("%s系统变量：%s - %s", enabled ? "启用" : "禁用", variable.getVariableType(), variable.getVariableName()),
                BigDecimal.ZERO, null);

        log.info("系统变量状态切换成功：variableId={}, enabled={}, operatorId={}", variableId, enabled, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultVariable(Long variableId, Long operatorId) {
        // 1. 查询变量是否存在
        SystemVariable variable = systemVariableMapper.selectById(variableId);
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }

        // 2. 取消同类型的其他默认变量
        List<SystemVariable> sameTypeVariables = systemVariableMapper.selectList(new LambdaQueryWrapper<SystemVariable>()
                .eq(SystemVariable::getVariableType, variable.getVariableType())
                .eq(SystemVariable::getIsDefault, 1));

        for (SystemVariable sameTypeVariable : sameTypeVariables) {
            sameTypeVariable.setIsDefault(0);
            systemVariableMapper.updateById(sameTypeVariable);
        }

        // 3. 设置当前变量为默认
        variable.setIsDefault(1);
        systemVariableMapper.updateById(variable);

        // 4. 记录操作日志
        operationLogService.log(operatorId, "设置默认变量", 
                String.format("设置默认变量：%s - %s", variable.getVariableType(), variable.getVariableName()),
                BigDecimal.ZERO, null);

        log.info("默认变量设置成功：variableId={}, operatorId={}", variableId, operatorId);
    }
}
