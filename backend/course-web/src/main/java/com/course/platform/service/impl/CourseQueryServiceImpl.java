package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.dto.aqks.AqksLoginResult;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.external.AqksApiClient;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.course.CourseQueryService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.platform.PlatformDockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 查课服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseQueryServiceImpl implements CourseQueryService {

    private final CoursePlatformMapper coursePlatformMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final PlatformDockingService platformDockingService;
    private final AqksApiClient aqksApiClient;

    @Override
    public CourseInfoResponse queryCourses(QueryCourseRequest request, Long userId) {
        // 1. 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 查询课程平台信息
        CoursePlatform platform = coursePlatformMapper.selectById(request.getPlatformId());
        if (platform == null) {
            throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }

        // 3. 记录查课日志
        String logDesc = String.format("查课：%s - %s",
                platform.getName(), request.getSchoolName());
        operationLogService.log(userId, "查课", logDesc, BigDecimal.ZERO, null);

        // 4. 查询课程
        List<CourseInfoResponse.CourseItem> courses;
        
        // 如果配置了查课API，则调用第三方接口
        if (platform.getQueryApiId() != null) {
            try {
                courses = platformDockingService.queryCourses(platform, request);
            } catch (Exception e) {
                log.warn("第三方查课失败: reason={}", e.getClass().getSimpleName());
                throw new BusinessException("第三方查课服务暂不可用");
            }
        } else if (platform.getIsSelfOperated() != null && platform.getIsSelfOperated() == 1) {
            // 自营平台：调用真实的查课接口
            courses = querySelfOperatedCourses(platform, request);
        } else {
            // 无API配置且非自营，使用模拟数据（用于测试）
            courses = mockQueryCourses(platform, request);
        }

        // 5. 构建响应
        return CourseInfoResponse.builder()
                .studentName(request.getStudentAccount())
                .studentAccount(request.getStudentAccount())
                .schoolName(request.getSchoolName())
                .courses(courses)
                .message("查询成功")
                .build();
    }

    /**
     * 自营平台查课 - 调用实验室安全平台API
     * 
     * API规范：
     * - POST https://aqks.csuft.edu.cn/api/MyUserInfo?UserName={学号}&isBackground=false
     * - Body: 双层Base64编码的密码 "{学号}@{密码}"
     * - 返回用户信息包含课程学习信息
     */
    private List<CourseInfoResponse.CourseItem> querySelfOperatedCourses(CoursePlatform platform, QueryCourseRequest request) {
        String studentAccount = request.getStudentAccount();
        log.info("[自营查课] platformId={}", platform.getId());

        AqksLoginResult login = aqksApiClient.login(studentAccount, request.getStudentPassword());
        if (!login.isSuccess()) {
            throw new BusinessException("实验室安全平台认证失败");
        }
        String studyTimes = login.getStudyTimes() == null ? "0" : login.getStudyTimes();
        String minimum = login.getMinTimeMinute() == null ? "0" : login.getMinTimeMinute();
        String progress = calculateProgress(studyTimes, minimum);
        int grade = login.getGrade() == null ? 0 : login.getGrade();
        CourseInfoResponse.CourseItem item = CourseInfoResponse.CourseItem.builder()
                .id("aqks_" + grade)
                .name(grade + "级实验室安全考试课程")
                .description(String.format("学生: %s | 学院: %s | 专业: %s | 已学习: %s分钟 | 要求: %s分钟 | 进度: %s",
                        login.getName() == null ? studentAccount : login.getName(),
                        login.getDepartmentName() == null ? "" : login.getDepartmentName(),
                        login.getSpecialtyName() == null ? "" : login.getSpecialtyName(),
                        studyTimes, minimum, progress))
                .endTime("2025-12-31")
                .selected(false)
                .build();
        return List.of(item);
    }

    /**
     * 计算学习进度百分比
     */
    private String calculateProgress(String studyTimes, String minTimeMinute) {
        try {
            int studied = Integer.parseInt(studyTimes);
            int required = Integer.parseInt(minTimeMinute);
            if (required <= 0) return "0%";
            int percent = Math.min(100, (studied * 100) / required);
            return percent + "%";
        } catch (NumberFormatException e) {
            return "0%";
        }
    }

    /**
     * 模拟查询课程（用于测试）
     */
    private List<CourseInfoResponse.CourseItem> mockQueryCourses(CoursePlatform platform, QueryCourseRequest request) {
        log.info("[模拟查课] platformId={}", platform.getId());

        List<CourseInfoResponse.CourseItem> courses = new ArrayList<>();
        
        courses.add(CourseInfoResponse.CourseItem.builder()
                .id("mock_001")
                .name("模拟测试课程")
                .description("这是模拟数据，非自营平台")
                .endTime("2025-12-31")
                .selected(false)
                .build());

        return courses;
    }
}
