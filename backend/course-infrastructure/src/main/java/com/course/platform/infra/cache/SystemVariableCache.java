package com.course.platform.infra.cache;

import com.course.platform.common.constant.Constants;
import com.course.platform.domain.entity.SystemVariable;
import com.course.platform.infra.persistence.mapper.SystemVariableMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统变量缓存
 * 
 * 启动时从数据库加载所有系统变量到内存，统一管理状态常量
 * Source: AURA-X-KYS - 统一状态管理，消除双重系统
 * 
 * 使用示例：
 * <pre>
 * // 获取订单状态值
 * int pending = SystemVariableCache.getStatusValue("order_status", "pending");
 * 
 * // 获取状态名称
 * String name = SystemVariableCache.getStatusName("order_status", "1");
 * 
 * // 判断状态是否启用
 * boolean enabled = SystemVariableCache.isStatusEnabled("order_status", "pending");
 * </pre>
 * 
 * @author AI Assistant
 * @since 2025-12-21
 */
@Slf4j
@Component
public class SystemVariableCache implements ApplicationRunner {
    
    private final SystemVariableMapper systemVariableMapper;
    
    /**
     * 缓存结构：变量类型 -> 变量Key -> SystemVariable
     * 例如：order_status -> pending -> SystemVariable(value=0, name=待处理)
     */
    private static final Map<String, Map<String, SystemVariable>> CACHE = new ConcurrentHashMap<>();
    
    /**
     * 值映射缓存：变量类型 -> 变量值 -> SystemVariable
     * 例如：order_status -> 0 -> SystemVariable(key=pending, name=待处理)
     */
    private static final Map<String, Map<String, SystemVariable>> VALUE_CACHE = new ConcurrentHashMap<>();
    
    public SystemVariableCache(SystemVariableMapper systemVariableMapper) {
        this.systemVariableMapper = systemVariableMapper;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        loadVariables();
        log.info("[系统变量缓存] 启动完成，已加载 {} 种变量类型", CACHE.size());
    }
    
    /**
     * 从数据库加载所有系统变量
     */
    public synchronized void loadVariables() {
        CACHE.clear();
        VALUE_CACHE.clear();
        
        List<SystemVariable> variables = systemVariableMapper.selectList(null);
        if (variables == null || variables.isEmpty()) {
            log.warn("[系统变量缓存] 数据库中没有系统变量数据");
            return;
        }
        
        for (SystemVariable variable : variables) {
            String type = variable.getVariableType();
            String key = variable.getVariableKey();
            String value = variable.getVariableValue();
            
            // 按类型和Key缓存
            CACHE.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                  .put(key, variable);
            
            // 按类型和值缓存（方便反向查询）
            VALUE_CACHE.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                       .put(value, variable);
        }
        
        log.info("[系统变量缓存] 加载完成: {} 个变量", variables.size());
    }
    
    /**
     * 获取状态值（整数）
     * 
     * @param variableType 变量类型，如 "order_status"
     * @param variableKey 变量键，如 "pending"
     * @return 状态值，如果不存在则返回 -1
     */
    public static int getStatusValue(String variableType, String variableKey) {
        try {
            SystemVariable variable = getVariable(variableType, variableKey);
            if (variable != null && variable.getVariableValue() != null) {
                return Integer.parseInt(variable.getVariableValue());
            }
        } catch (NumberFormatException e) {
            log.error("[系统变量缓存] 解析状态值失败: type={}, key={}", variableType, variableKey, e);
        }
        
        // 回退到Constants常量（保持向后兼容）
        return getDefaultValue(variableType, variableKey);
    }
    
    /**
     * 获取状态名称
     * 
     * @param variableType 变量类型
     * @param variableValue 变量值
     * @return 状态名称，如果不存在则返回 "未知"
     */
    public static String getStatusName(String variableType, String variableValue) {
        Map<String, SystemVariable> valueMap = VALUE_CACHE.get(variableType);
        if (valueMap != null) {
            SystemVariable variable = valueMap.get(variableValue);
            if (variable != null) {
                return variable.getVariableName();
            }
        }
        return "未知";
    }
    
    /**
     * 获取完整的SystemVariable对象
     * 
     * @param variableType 变量类型
     * @param variableKey 变量键
     * @return SystemVariable对象，如果不存在则返回null
     */
    public static SystemVariable getVariable(String variableType, String variableKey) {
        Map<String, SystemVariable> typeMap = CACHE.get(variableType);
        if (typeMap != null) {
            return typeMap.get(variableKey);
        }
        return null;
    }
    
    /**
     * 判断状态是否启用
     * 
     * @param variableType 变量类型
     * @param variableKey 变量键
     * @return 是否启用
     */
    public static boolean isStatusEnabled(String variableType, String variableKey) {
        SystemVariable variable = getVariable(variableType, variableKey);
        return variable != null && variable.getIsEnabled() != null && variable.getIsEnabled() == 1;
    }
    
    /**
     * 获取某类型的所有变量
     * 
     * @param variableType 变量类型
     * @return 变量Map
     */
    public static Map<String, SystemVariable> getVariablesByType(String variableType) {
        return CACHE.getOrDefault(variableType, new ConcurrentHashMap<>());
    }
    
    /**
     * 获取默认值（从Constants类）
     * 用于数据库数据缺失时的回退方案
     * 
     * @param variableType 变量类型
     * @param variableKey 变量键
     * @return 默认值
     */
    private static int getDefaultValue(String variableType, String variableKey) {
        // 订单状态默认值
        if ("order_status".equals(variableType)) {
            switch (variableKey) {
                case "pending": return Constants.ORDER_STATUS_PENDING;
                case "processing": return Constants.ORDER_STATUS_PROCESSING;
                case "completed": return Constants.ORDER_STATUS_COMPLETED;
                case "cancelled": return Constants.ORDER_STATUS_CANCELLED;
                case "failed": return Constants.ORDER_STATUS_FAILED;
                case "exam_pending": return Constants.ORDER_STATUS_EXAM_PENDING;
                case "exam_processing": return Constants.ORDER_STATUS_EXAM_PROCESSING;
                case "exam_completed": return Constants.ORDER_STATUS_EXAM_COMPLETED;
                case "refund_pending": return Constants.ORDER_STATUS_REFUND_PENDING;
            }
        }
        // 对接状态默认值
        else if ("dock_status".equals(variableType)) {
            switch (variableKey) {
                case "pending": return 0;
                case "success": return 1;
                case "failed": return 2;
                case "duplicate": return 3;
                case "cancelled": return 4;
            }
        }
        // 用户状态默认值
        else if ("user_status".equals(variableType)) {
            switch (variableKey) {
                case "disabled": return Constants.USER_STATUS_DISABLED;
                case "normal": return Constants.USER_STATUS_NORMAL;
            }
        }
        // 卡密状态默认值
        else if ("card_status".equals(variableType)) {
            switch (variableKey) {
                case "unused": return Constants.CARD_STATUS_UNUSED;
                case "used": return Constants.CARD_STATUS_USED;
                case "disabled": return Constants.CARD_STATUS_DISABLED;
            }
        }
        
        log.warn("[系统变量缓存] 未找到变量且无默认值: type={}, key={}", variableType, variableKey);
        return -1;
    }
    
    /**
     * 手动刷新缓存（用于运行时更新）
     */
    public void refresh() {
        log.info("[系统变量缓存] 开始刷新缓存");
        loadVariables();
    }
}
