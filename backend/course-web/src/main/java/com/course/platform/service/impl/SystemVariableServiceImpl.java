package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.system.SystemVariableService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.SystemVariableCreateRequest;
import com.course.platform.domain.dto.SystemVariableUpdateRequest;
import com.course.platform.domain.entity.SystemVariable;
import com.course.platform.domain.support.ThemeVariableCatalog;
import com.course.platform.infra.persistence.mapper.SystemVariableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统变量管理服务实现类。
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
        validateThemeVariable(request.getVariableType(), request.getVariableKey(), request.getVariableValue());

        SystemVariable existingVariable = systemVariableMapper.selectOne(new LambdaQueryWrapper<SystemVariable>()
                .eq(SystemVariable::getVariableKey, request.getVariableKey())
                .eq(SystemVariable::getVariableType, request.getVariableType()));
        if (existingVariable != null) {
            throw new BusinessException("该变量键名在指定类型下已存在");
        }

        SystemVariable variable = new SystemVariable();
        variable.setVariableKey(request.getVariableKey().trim());
        variable.setVariableName(request.getVariableName().trim());
        variable.setVariableType(request.getVariableType().trim());
        variable.setVariableValue(request.getVariableValue().trim());
        variable.setVariableLabel(request.getVariableLabel());
        variable.setSortOrder(request.getSortOrder());
        variable.setIsDefault(isThemeVariable(variable) ? 0 : request.getIsDefault());
        variable.setIsEnabled(request.getIsEnabled());
        variable.setColor(isThemeVariable(variable) ? null : request.getColor());
        variable.setIcon(isThemeVariable(variable) ? null : request.getIcon());
        systemVariableMapper.insert(variable);

        operationLogService.log(operatorId, "创建系统变量",
                String.format("创建系统变量：%s - %s", request.getVariableType(), request.getVariableName()),
                BigDecimal.ZERO, null);
        log.info("系统变量创建成功：variableId={}, operatorId={}", variable.getId(), operatorId);
        return variable.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVariable(SystemVariableUpdateRequest request, Long operatorId) {
        SystemVariable variable = systemVariableMapper.selectById(request.getId());
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }

        if (isThemeVariable(variable)
                && (!variable.getVariableType().equals(request.getVariableType())
                || !variable.getVariableKey().equals(request.getVariableKey()))) {
            throw new BusinessException("主题变量的键名和类型不可修改");
        }
        validateThemeVariable(request.getVariableType(), request.getVariableKey(), request.getVariableValue());

        SystemVariable existingVariable = systemVariableMapper.selectOne(new LambdaQueryWrapper<SystemVariable>()
                .eq(SystemVariable::getVariableKey, request.getVariableKey())
                .eq(SystemVariable::getVariableType, request.getVariableType())
                .ne(SystemVariable::getId, request.getId()));
        if (existingVariable != null) {
            throw new BusinessException("该变量键名在指定类型下已存在");
        }

        variable.setVariableKey(request.getVariableKey().trim());
        variable.setVariableName(request.getVariableName().trim());
        variable.setVariableType(request.getVariableType().trim());
        variable.setVariableValue(request.getVariableValue().trim());
        variable.setVariableLabel(request.getVariableLabel());
        variable.setSortOrder(request.getSortOrder());
        variable.setIsDefault(isThemeVariable(variable) ? 0 : request.getIsDefault());
        variable.setIsEnabled(request.getIsEnabled());
        variable.setColor(isThemeVariable(variable) ? null : request.getColor());
        variable.setIcon(isThemeVariable(variable) ? null : request.getIcon());
        systemVariableMapper.updateById(variable);

        operationLogService.log(operatorId, "更新系统变量",
                String.format("更新系统变量：%s - %s", request.getVariableType(), request.getVariableName()),
                BigDecimal.ZERO, null);
        log.info("系统变量更新成功：variableId={}, operatorId={}", request.getId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateThemeVariables(List<SystemVariableUpdateRequest> requests, Long operatorId) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("主题变量更新列表不能为空");
        }
        if (requests.size() > ThemeVariableCatalog.DEFINITIONS.size()) {
            throw new BusinessException("一次更新的主题变量数量超出限制");
        }
        for (SystemVariableUpdateRequest request : requests) {
            SystemVariable existing = systemVariableMapper.selectById(request.getId());
            if (existing == null || !isThemeVariable(existing)) {
                throw new BusinessException("批量主题接口只能更新系统内置主题变量");
            }
            updateVariable(request, operatorId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVariable(Long variableId, Long operatorId) {
        SystemVariable variable = systemVariableMapper.selectById(variableId);
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }
        if (isThemeVariable(variable)) {
            throw new BusinessException("系统内置主题变量不能删除，可通过禁用恢复前端默认色");
        }
        if (variable.getIsDefault() == 1) {
            throw new BusinessException("默认变量不能删除");
        }

        systemVariableMapper.deleteById(variableId);
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
    public IPage<SystemVariable> queryVariables(String variableType, String keyword, Integer page, Integer pageSize) {
        Page<SystemVariable> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<SystemVariable> queryWrapper = new LambdaQueryWrapper<>();

        if (hasText(variableType)) {
            queryWrapper.eq(SystemVariable::getVariableType, variableType.trim());
        }
        if (hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(SystemVariable::getVariableKey, normalizedKeyword)
                    .or().like(SystemVariable::getVariableName, normalizedKeyword)
                    .or().like(SystemVariable::getVariableValue, normalizedKeyword)
                    .or().like(SystemVariable::getVariableLabel, normalizedKeyword));
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
    public Map<String, Map<String, String>> getEnabledThemeVariables() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        result.put("light", new LinkedHashMap<>());
        result.put("dark", new LinkedHashMap<>());

        List<SystemVariable> variables = systemVariableMapper.selectList(new LambdaQueryWrapper<SystemVariable>()
                .in(SystemVariable::getVariableType, ThemeVariableCatalog.types())
                .eq(SystemVariable::getIsEnabled, 1)
                .orderByAsc(SystemVariable::getSortOrder)
                .orderByAsc(SystemVariable::getId));

        for (SystemVariable variable : variables) {
            if (!ThemeVariableCatalog.isKnownKey(variable.getVariableKey())
                    || !ThemeVariableCatalog.isSupportedColor(variable.getVariableValue())) {
                log.warn("忽略无效主题变量：type={}, key={}", variable.getVariableType(), variable.getVariableKey());
                continue;
            }
            String mode = ThemeVariableCatalog.DARK_TYPE.equals(variable.getVariableType()) ? "dark" : "light";
            result.get(mode).put(variable.getVariableKey(), variable.getVariableValue().trim());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleVariableStatus(Long variableId, Boolean enabled, Long operatorId) {
        SystemVariable variable = systemVariableMapper.selectById(variableId);
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }

        variable.setIsEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        systemVariableMapper.updateById(variable);
        operationLogService.log(operatorId, Boolean.TRUE.equals(enabled) ? "启用系统变量" : "禁用系统变量",
                String.format("%s系统变量：%s - %s", Boolean.TRUE.equals(enabled) ? "启用" : "禁用",
                        variable.getVariableType(), variable.getVariableName()),
                BigDecimal.ZERO, null);
        log.info("系统变量状态切换成功：variableId={}, enabled={}, operatorId={}", variableId, enabled, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultVariable(Long variableId, Long operatorId) {
        SystemVariable variable = systemVariableMapper.selectById(variableId);
        if (variable == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "系统变量不存在");
        }
        if (isThemeVariable(variable)) {
            throw new BusinessException("主题颜色不使用默认选项，请使用恢复默认色功能");
        }

        List<SystemVariable> sameTypeVariables = systemVariableMapper.selectList(new LambdaQueryWrapper<SystemVariable>()
                .eq(SystemVariable::getVariableType, variable.getVariableType())
                .eq(SystemVariable::getIsDefault, 1));
        for (SystemVariable sameTypeVariable : sameTypeVariables) {
            sameTypeVariable.setIsDefault(0);
            systemVariableMapper.updateById(sameTypeVariable);
        }

        variable.setIsDefault(1);
        systemVariableMapper.updateById(variable);
        operationLogService.log(operatorId, "设置默认变量",
                String.format("设置默认变量：%s - %s", variable.getVariableType(), variable.getVariableName()),
                BigDecimal.ZERO, null);
        log.info("默认变量设置成功：variableId={}, operatorId={}", variableId, operatorId);
    }

    private void validateThemeVariable(String variableType, String variableKey, String variableValue) {
        if (!ThemeVariableCatalog.isThemeType(variableType)) {
            return;
        }
        if (!ThemeVariableCatalog.isKnownKey(variableKey)) {
            throw new BusinessException("不支持的主题变量键名");
        }
        if (!ThemeVariableCatalog.isSupportedColor(variableValue)) {
            throw new BusinessException("主题变量值必须是有效的 HEX、RGB(A) 或 HSL(A) 颜色");
        }
    }

    private boolean isThemeVariable(SystemVariable variable) {
        return ThemeVariableCatalog.isThemeType(variable.getVariableType());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
