package com.course.platform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.domain.entity.SystemVariable;
import com.course.platform.domain.support.ThemeVariableCatalog;
import com.course.platform.infra.persistence.mapper.SystemVariableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 为升级后的现有数据库补齐主题变量。只插入缺失项，绝不覆盖管理员已有配置。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class ThemeVariableInitializer implements ApplicationRunner {

    private final SystemVariableMapper systemVariableMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        List<SystemVariable> existing = systemVariableMapper.selectList(
                new LambdaQueryWrapper<SystemVariable>()
                        .in(SystemVariable::getVariableType, ThemeVariableCatalog.types())
        );
        Set<String> existingKeys = new HashSet<>();
        for (SystemVariable variable : existing) {
            existingKeys.add(variable.getVariableType() + ":" + variable.getVariableKey());
        }

        int inserted = 0;
        for (String type : ThemeVariableCatalog.types()) {
            for (ThemeVariableCatalog.Definition definition : ThemeVariableCatalog.DEFINITIONS) {
                if (existingKeys.contains(type + ":" + definition.key())) {
                    continue;
                }

                SystemVariable variable = new SystemVariable();
                variable.setVariableKey(definition.key());
                variable.setVariableName(definition.name());
                variable.setVariableType(type);
                variable.setVariableValue(definition.valueForType(type));
                variable.setVariableLabel(definition.description());
                variable.setSortOrder(definition.sortOrder());
                variable.setIsDefault(0);
                variable.setIsEnabled(1);
                variable.setColor(null);
                variable.setIcon(null);
                systemVariableMapper.insert(variable);
                inserted++;
            }
        }

        if (inserted > 0) {
            log.info("[主题变量] 已补齐 {} 个默认主题颜色配置", inserted);
        }
    }
}
