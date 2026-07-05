package com.course.platform.infrastructure.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 日志切面
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    /**
     * 定义切点：所有Controller方法
     */
    @Pointcut("execution(* com.course.platform.controller..*.*(..))")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：记录方法执行时间
     */
    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        try {
            Object result = joinPoint.proceed();
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            log.info("{}#{} 执行完成，耗时: {}ms", className, methodName, elapsedTime);
            
            return result;
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.error("{}#{} 执行失败，耗时: {}ms，异常: {}", className, methodName, elapsedTime, e.getMessage());
            throw e;
        }
    }
}

