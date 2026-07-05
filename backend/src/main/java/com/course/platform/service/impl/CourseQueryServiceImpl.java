package com.course.platform.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.ResultCode;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.User;
import com.course.platform.mapper.CoursePlatformMapper;
import com.course.platform.mapper.UserMapper;
import com.course.platform.service.CourseQueryService;
import com.course.platform.service.OperationLogService;
import com.course.platform.service.PlatformDockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
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

    // 实验室安全平台API地址
    private static final String AQKS_API_URL = "https://aqks.csuft.edu.cn/api/MyUserInfo";

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
        String logDesc = String.format("查课：%s - %s %s", 
                platform.getName(), request.getSchoolName(), request.getStudentAccount());
        operationLogService.log(userId, "查课", logDesc, BigDecimal.ZERO, null);

        // 4. 查询课程
        List<CourseInfoResponse.CourseItem> courses;
        
        // 如果配置了查课API，则调用第三方接口
        if (platform.getQueryApiId() != null) {
            try {
                courses = platformDockingService.queryCourses(platform, request);
            } catch (Exception e) {
                log.error("第三方查课失败: {}", e.getMessage());
                throw new BusinessException("查课失败: " + e.getMessage());
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
        String studentPassword = request.getStudentPassword();
        
        log.info("[自营查课] platform={}, account={}", platform.getName(), studentAccount);
        
        try {
            // 1. 构建请求URL
            String url = AQKS_API_URL + "?UserName=" + studentAccount + "&isBackground=false";
            
            // 2. 双层Base64编码密码："{学号}@{密码}"
            String rawCredential = studentPassword;
            String encodedPassword = doubleBase64Encode(rawCredential);
            
            // 3. 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Apifox/1.0.0 (https://apifox.com)");
            headers.setAccept(Collections.singletonList(MediaType.ALL));
            headers.set("Host", "aqks.csuft.edu.cn");
            headers.set("Connection", "keep-alive");
            
            // 4. 发送请求 - Body为双层Base64编码的密码（JSON字符串格式，带引号）
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<String> entity = new HttpEntity<>("\"" + encodedPassword + "\"", headers);
            
            log.info("[自营查课] 请求URL: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            String responseBody = response.getBody();
            
            log.info("[自营查课] 响应状态: {}", response.getStatusCode());
            log.debug("[自营查课] 响应Body: {}", responseBody);
            
            // 5. 解析响应
            return parseAqksResponse(responseBody, studentAccount);
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[自营查课] 查课失败: {}", e.getMessage(), e);
            throw new BusinessException("实验室安全平台查课失败: " + e.getMessage());
        }
    }

    /**
     * 双层Base64编码
     * 规则：
     * 1. 第一次编码：将原始字符串编码为Base64
     * 2. 第二次编码：将第一次编码结果再次编码为Base64
     */
    private String doubleBase64Encode(String raw) {
        String once = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        String twice = Base64.getEncoder().encodeToString(once.getBytes(StandardCharsets.UTF_8));
        return twice;
    }

    /**
     * 解析实验室安全平台API响应
     * 
     * 成功响应包含 Token 字段
     * 失败响应: {"Code": -9999, "Message": "错误信息"}
     */
    private List<CourseInfoResponse.CourseItem> parseAqksResponse(String responseBody, String studentAccount) {
        List<CourseInfoResponse.CourseItem> courses = new ArrayList<>();
        
        if (responseBody == null || responseBody.isEmpty()) {
            throw new BusinessException("实验室安全平台返回空响应");
        }
        
        try {
            JSONObject json = JSONUtil.parseObj(responseBody);
            
            // 检查是否有错误码 (Code=-9999 表示失败)
            if (json.containsKey("Code") && json.getInt("Code", 0) == -9999) {
                String errorMsg = json.getStr("Message", "账号或密码错误");
                throw new BusinessException("实验室安全平台: " + errorMsg);
            }
            
            // 检查是否登录成功 - 返回包含Token字段表示成功
            if (json.containsKey("Token") && json.getStr("Token") != null) {
                String studentName = json.getStr("Name", studentAccount);
                String studyTimes = json.getStr("StudyTimes", "0");
                String minTimeMinute = json.getStr("MinTimeMinute", "0");
                String departmentName = json.getStr("DepartmentName", "");
                String specialtyName = json.getStr("SpecialtyName", "");
                Integer grade = json.getInt("Grade", 0);
                
                // 计算学习进度
                String progress = calculateProgress(studyTimes, minTimeMinute);
                
                // 构建课程项 (进度信息放入description)
                CourseInfoResponse.CourseItem courseItem = CourseInfoResponse.CourseItem.builder()
                        .id("aqks_" + grade)
                        .name(grade + "级实验室安全考试课程")
                        .description(String.format("学生: %s | 学院: %s | 专业: %s | 已学习: %s分钟 | 要求: %s分钟 | 进度: %s",
                                studentName, departmentName, specialtyName, studyTimes, minTimeMinute, progress))
                        .endTime("2025-12-31")
                        .selected(false)
                        .build();
                
                courses.add(courseItem);
                
                log.info("[自营查课] 成功: 学生={}, 已学习={}分钟, 要求={}分钟, 进度={}", 
                        studentName, studyTimes, minTimeMinute, progress);
            } else {
                // 未知响应格式
                throw new BusinessException("实验室安全平台返回未知格式");
            }
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[自营查课] 解析响应失败: {}", e.getMessage(), e);
            throw new BusinessException("解析实验室安全平台响应失败");
        }
        
        return courses;
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
        log.info("[模拟查课] platform={}, account={}", platform.getName(), request.getStudentAccount());

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
