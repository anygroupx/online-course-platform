package com.course.platform.infra.docking.impl;

import cn.hutool.core.util.StrUtil;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.infra.external.AqksApiClient;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.dto.aqks.AqksExamInfo;
import com.course.platform.domain.dto.aqks.AqksLoginResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AQKS实验室安全平台对接策略
 * 
 * 自营平台实现，直接调用AQKS平台API
 * 
 * @author AI Assistant
 * @since 2025-12-20
 */
@Slf4j
@Component
public class AqksDockingStrategy implements PlatformDockingStrategy {
    
    private final AqksApiClient aqksApiClient;
    
    /**
     * 每次刷时长增量（秒）- 默认10秒
     */
    private static final int STUDY_TIME_DELTA_SECONDS = 10;
    
    public AqksDockingStrategy(AqksApiClient aqksApiClient) {
        this.aqksApiClient = aqksApiClient;
    }
    
    @Override
    public String getProviderType() {
        return "aqks";  // 自营平台标识
    }
    
    /**
     * 查询课程（登录并获取用户学习状态）
     */
    @Override
    public List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request, ApiProvider apiProvider) {
        String studentAccount = request.getStudentAccount();
        String studentPassword = request.getStudentPassword();
        
        log.info("[AQKS查课] platform={}", platform.getName());
        
        // 登录获取用户信息
        AqksLoginResult loginResult = aqksApiClient.login(studentAccount, studentPassword);
        
        if (!loginResult.isSuccess()) {
            log.warn("[AQKS查课] 登录失败，已省略第三方错误详情");
            throw new RuntimeException("AQKS登录失败: " + loginResult.getErrorMessage());
        }
        
        List<CourseInfoResponse.CourseItem> courses = new ArrayList<>();
        
        // 构建课程项 - 包含学习进度信息
        String progress = loginResult.calculateProgress() + "%";
        String description = String.format(
                "学生: %s | 学院: %s | 专业: %s | 已学习: %s分钟 | 要求: %s分钟 | 进度: %s",
                loginResult.getName(),
                loginResult.getDepartmentName(),
                loginResult.getSpecialtyName(),
                loginResult.getStudyTimes(),
                loginResult.getMinTimeMinute(),
                progress
        );
        
        // 添加主课程
        CourseInfoResponse.CourseItem mainCourse = CourseInfoResponse.CourseItem.builder()
                .id("aqks_" + loginResult.getGrade())
                .name(loginResult.getGrade() + "级实验室安全考试课程")
                .description(description)
                .endTime("2025-12-31")
                .selected(false)
                .build();
        courses.add(mainCourse);
        
        // 尝试获取考试信息
        try {
            List<AqksExamInfo> examList = aqksApiClient.getExamList(loginResult.getServerCookie());
            for (AqksExamInfo exam : examList) {
                String examDesc = String.format(
                        "考试: %s | 分数: %d | 及格线: %d | %s",
                        exam.getCourseName(),
                        exam.getScore() != null ? exam.getScore() : 0,
                        exam.getBorderLine() != null ? exam.getBorderLine() : 0,
                        Boolean.TRUE.equals(exam.getIsPassed()) ? "✅ 已通过" : "❌ 未通过"
                );
                
                CourseInfoResponse.CourseItem examCourse = CourseInfoResponse.CourseItem.builder()
                        .id("aqks_exam_" + exam.getCourseId())
                        .name(exam.getCourseName())
                        .description(examDesc)
                        .endTime("2025-12-31")
                        .selected(false)
                        .build();
                courses.add(examCourse);
            }
        } catch (Exception e) {
            log.warn("[AQKS查课] 获取考试信息失败: exceptionType={}", e.getClass().getSimpleName());
        }
        
        log.info("[AQKS查课] 成功: 进度={}", progress);
        
        return courses;
    }
    
    /**
     * 对接下单 - 自营平台直接标记成功，等待后续手动/自动刷课
     */
    @Override
    public DockResult dockOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        log.info("[AQKS下单] orderId={}, orderNo={}", order.getId(), order.getOrderNo());
        
        // 验证账号密码是否能登录
        AqksLoginResult loginResult = aqksApiClient.login(
                order.getStudentAccount(), 
                order.getStudentPassword()
        );
        
        if (!loginResult.isSuccess()) {
            log.warn("[AQKS下单] 账号验证失败，已省略第三方错误详情");
            return DockResult.fail("账号验证失败: " + loginResult.getErrorMessage());
        }
        
        // 保存用户ID到备注（用于后续刷时长）
        String thirdOrderId = loginResult.getUserId();
        
        log.info("[AQKS下单] 成功，当前进度={}%", loginResult.calculateProgress());
        
        return DockResult.success("自营订单创建成功", thirdOrderId);
    }
    
    /**
     * 查询订单进度 - 登录获取实时学习进度
     */
    @Override
    public OrderProgressResult queryOrderProgress(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        log.info("[AQKS查进度] orderId={}", order.getId());
        
        // 登录获取最新状态
        AqksLoginResult loginResult = aqksApiClient.login(
                order.getStudentAccount(), 
                order.getStudentPassword()
        );
        
        if (!loginResult.isSuccess()) {
            log.warn("[AQKS查进度] 登录失败，已省略第三方错误详情");
            return OrderProgressResult.builder()
                    .progress(order.getProgress())
                    .orderStatus(order.getOrderStatus())
                    .remarks("查询失败: " + loginResult.getErrorMessage())
                    .build();
        }
        
        // 计算进度
        int progress = loginResult.calculateProgress();
        boolean completed = loginResult.isStudyCompleted();
        
        // 构建进度描述
        String progressStr = String.format("%d%% (%s/%s分钟)", 
                progress, 
                loginResult.getStudyTimes(), 
                loginResult.getMinTimeMinute());
        
        // 确定订单状态
        Integer orderStatus = order.getOrderStatus();
        if (completed) {
            // 时长已满足，检查考试情况
            try {
                List<AqksExamInfo> examList = aqksApiClient.getExamList(loginResult.getServerCookie());
                boolean allPassed = examList.stream().allMatch(e -> Boolean.TRUE.equals(e.getIsPassed()));
                if (allPassed && !examList.isEmpty()) {
                    orderStatus = SystemVariableCache.getStatusValue("order_status", "completed");
                } else {
                    // 时长完成但考试未通过
                    orderStatus = SystemVariableCache.getStatusValue("order_status", "processing");
                }
            } catch (Exception e) {
                log.warn("[AQKS查进度] 获取考试状态失败: exceptionType={}", e.getClass().getSimpleName());
            }
        }
        
        String remarks = String.format("学生: %s | 学院: %s | %s", 
                loginResult.getName(),
                loginResult.getDepartmentName(),
                completed ? "✅ 时长已满足" : "⏳ 学习中");
        
        log.info("[AQKS查进度] 进度={}, 状态={}", progressStr, orderStatus);
        
        return OrderProgressResult.builder()
                .progress(progressStr)
                .orderStatus(orderStatus)
                .remarks(remarks)
                .thirdOrderId(loginResult.getUserId())
                .build();
    }
    
    /**
     * 补单/重试 - 触发一次刷时长操作
     */
    @Override
    public DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        log.info("[AQKS补单] orderId={}", order.getId());
        
        // 先登录获取Cookie
        AqksLoginResult loginResult = aqksApiClient.login(
                order.getStudentAccount(), 
                order.getStudentPassword()
        );
        
        if (!loginResult.isSuccess()) {
            log.warn("[AQKS补单] 登录失败，已省略第三方错误详情");
            return DockResult.fail("登录失败: " + loginResult.getErrorMessage());
        }
        
        // 刷一次时长（单位：秒）
        boolean success = aqksApiClient.addStudyTime(
                loginResult.getUserId(), 
                STUDY_TIME_DELTA_SECONDS, 
                loginResult.getServerCookie()
        );
        
        if (success) {
            // 通过重新登录获取最新时长
            AqksLoginResult freshLogin = aqksApiClient.login(
                    order.getStudentAccount(), 
                    order.getStudentPassword()
            );
            String currentTime = freshLogin.isSuccess() ? freshLogin.getStudyTimes() : "未知";
            log.info("[AQKS补单] 刷时长成功: +{}秒, 当前累计={}分钟", STUDY_TIME_DELTA_SECONDS, currentTime);
            return DockResult.success("刷时长成功: +" + STUDY_TIME_DELTA_SECONDS + "秒，当前累计: " + currentTime + "分钟", loginResult.getUserId());
        } else {
            log.warn("[AQKS补单] 刷时长失败");
            return DockResult.fail("刷时长失败");
        }
    }
    
    /**
     * 获取平台列表 - 自营平台不需要此功能
     */
    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider) {
        // 自营平台无需获取外部平台列表
        return Collections.emptyList();
    }
}
