package com.course.platform.infra.external;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.course.platform.domain.dto.aqks.AqksExamInfo;
import com.course.platform.domain.dto.aqks.AqksLoginResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * AQKS实验室安全平台API客户端
 * 
 * 封装与AQKS平台的所有HTTP交互
 * 
 * @author AI Assistant
 * @since 2025-12-20
 */
@Slf4j
@Component
public class AqksApiClient {
    
    /**
     * AQKS平台基础URL
     */
    private static final String BASE_URL = "https://aqks.csuft.edu.cn";
    
    /**
     * API端点
     */
    private static final String API_LOGIN = "/api/MyUserInfo";
    private static final String API_STUDY_TIME_SET = "/api/LoginTimesSet";
    private static final String API_STUDY_TIME_GET = "/api/LoginTimesGet";
    private static final String API_MY_SCORES = "/api/MyScores";
    
    private final RestTemplate restTemplate;
    
    public AqksApiClient() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 登录AQKS平台
     * 
     * @param username 学号
     * @param password 密码
     * @return 登录结果
     */
    public AqksLoginResult login(String username, String password) {
        String url = BASE_URL + API_LOGIN + "?UserName=" + username + "&isBackground=false";
        
        log.info("[AQKS登录] 学号: {}", username);
        
        try {
            // 只对密码进行双层Base64编码
            String encodedPassword = doubleBase64Encode(password);
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.setAccept(Collections.singletonList(MediaType.ALL));
            headers.set("Host", "aqks.csuft.edu.cn");
            headers.set("Connection", "keep-alive");
            
            // Body为双层Base64编码的密码（JSON字符串格式，带引号）
            HttpEntity<String> entity = new HttpEntity<>("\"" + encodedPassword + "\"", headers);
            
            log.debug("[AQKS登录] 请求URL: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            String responseBody = response.getBody();
            
            log.info("[AQKS登录] 响应状态: {}", response.getStatusCode());
            log.debug("[AQKS登录] 响应Body: {}", responseBody);
            
            return parseLoginResponse(responseBody, username);
            
        } catch (Exception e) {
            log.error("[AQKS登录] 登录失败: {}", e.getMessage(), e);
            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage("登录失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 累加学习时长
     * 
     * @param userId AQKS用户ID
     * @param deltaSeconds 要累加的时长（秒），默认10秒
     * @param cookie 登录Cookie（serverCookie）
     * @return 是否成功
     */
    public boolean addStudyTime(String userId, int deltaSeconds, String cookie) {
        String url = BASE_URL + API_STUDY_TIME_SET + "?UserID=" + userId + "&StudyTimes=" + deltaSeconds;
        
        log.info("[AQKS刷时长] userId={}, delta={}秒", userId, deltaSeconds);
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            log.info("[AQKS刷时长] 响应状态: {}, Body: {}", response.getStatusCode(), response.getBody());
            
            // 检查响应是否成功
            if (response.getStatusCode().is2xxSuccessful()) {
                String body = response.getBody();
                // 某些成功返回可能是空或简单文本
                if (StrUtil.isBlank(body) || "true".equalsIgnoreCase(body)) {
                    return true;
                }
                // 尝试解析JSON
                if (body.trim().startsWith("{")) {
                    JSONObject json = JSONUtil.parseObj(body);
                    // 检查是否有success字段
                    if (json.containsKey("success") || json.containsKey("Success")) {
                        return json.getBool("success", json.getBool("Success", true));
                    }
                }
                return true;  // 默认认为成功
            }
            return false;
            
        } catch (Exception e) {
            log.error("[AQKS刷时长] 失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取当前学习时长（通过重新登录验证）
     * 
     * 注意：AQKS平台需要通过登录接口来获取最新的学习时长，
     * 而不是通过 LoginTimesGet 接口
     * 
     * @param username 学号
     * @param password 密码
     * @return 当前累计时长（分钟），失败返回-1
     */
    public int getStudyTimeByLogin(String username, String password) {
        log.debug("[AQKS读时长] 通过登录验证, username={}", username);
        
        try {
            AqksLoginResult result = login(username, password);
            if (result.isSuccess() && result.getStudyTimes() != null) {
                return Integer.parseInt(result.getStudyTimes());
            }
            return -1;
        } catch (Exception e) {
            log.error("[AQKS读时长] 登录验证失败: {}", e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * 获取当前学习时长（旧接口，可能不可用）
     * 
     * @param userId AQKS用户ID
     * @param cookie 登录Cookie
     * @return 当前累计时长（分钟），失败返回-1
     * @deprecated 使用 {@link #getStudyTimeByLogin(String, String)} 代替
     */
    @Deprecated
    public int getStudyTime(String userId, String cookie) {
        String url = BASE_URL + API_STUDY_TIME_GET + "?UserID=" + userId;
        
        log.debug("[AQKS读时长] userId={}", userId);
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            
            log.debug("[AQKS读时长] 响应: {}", body);
            
            if (StrUtil.isBlank(body)) {
                return -1;
            }
            
            // 纯数字返回
            if (body.trim().matches("\\d+")) {
                return Integer.parseInt(body.trim());
            }
            
            // JSON格式
            if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                JSONObject json;
                if (body.trim().startsWith("[")) {
                    JSONArray arr = JSONUtil.parseArray(body);
                    if (arr.isEmpty()) return -1;
                    json = arr.getJSONObject(0);
                } else {
                    json = JSONUtil.parseObj(body);
                }
                
                // 尝试多个可能的字段名
                for (String key : new String[]{"StudyTimes", "studyTimes", "TotalMinutes", "LoginCount"}) {
                    if (json.containsKey(key)) {
                        return json.getInt(key, -1);
                    }
                }
            }
            
            return -1;
            
        } catch (Exception e) {
            log.error("[AQKS读时长] 失败: {}", e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * 获取考试信息列表
     * 
     * @param cookie 登录Cookie
     * @return 考试信息列表
     */
    public List<AqksExamInfo> getExamList(String cookie) {
        String url = BASE_URL + API_MY_SCORES;
        
        log.debug("[AQKS考试列表] 请求");
        
        List<AqksExamInfo> examList = new ArrayList<>();
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            
            log.debug("[AQKS考试列表] 响应: {}", body);
            
            if (StrUtil.isBlank(body)) {
                return examList;
            }
            
            JSONArray arr = JSONUtil.parseArray(body);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject item = arr.getJSONObject(i);
                AqksExamInfo exam = AqksExamInfo.builder()
                        .courseId(item.getStr("CourseID"))
                        .courseName(item.getStr("CourseName"))
                        .testName(item.getStr("TestName"))
                        .testId(item.getInt("TestID", 0))
                        .testPaperId(item.getInt("TestPaperID", 0))
                        .isPassed(item.getBool("IsPassed", false))
                        .isJoined(item.getBool("IsJoined", false))
                        .score(item.getInt("Score", 0))
                        .borderLine(item.getInt("BorderLine", 0))
                        .unusedTime(item.getInt("UnusedTime", 0))
                        .testCounts(item.getInt("TestCounts", 0))
                        .build();
                examList.add(exam);
            }
            
            log.info("[AQKS考试列表] 获取到 {} 门考试", examList.size());
            
        } catch (Exception e) {
            log.error("[AQKS考试列表] 失败: {}", e.getMessage(), e);
        }
        
        return examList;
    }
    
    /**
     * 获取指定课程的考试详情
     * 
     * @param courseId 课程ID
     * @param cookie 登录Cookie
     * @return 考试信息
     */
    public AqksExamInfo getExamDetail(String courseId, String cookie) {
        String url = BASE_URL + API_MY_SCORES + "?CourseID=" + courseId;
        
        log.debug("[AQKS考试详情] courseId={}", courseId);
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            
            if (StrUtil.isBlank(body)) {
                return null;
            }
            
            JSONArray arr = JSONUtil.parseArray(body);
            if (arr.isEmpty()) {
                return null;
            }
            
            JSONObject item = arr.getJSONObject(0);
            return AqksExamInfo.builder()
                    .courseId(item.getStr("CourseID"))
                    .courseName(item.getStr("CourseName"))
                    .testName(item.getStr("TestName"))
                    .testId(item.getInt("TestID", 0))
                    .testPaperId(item.getInt("TestPaperID", 0))
                    .isPassed(item.getBool("IsPassed", false))
                    .isJoined(item.getBool("IsJoined", false))
                    .score(item.getInt("Score", 0))
                    .borderLine(item.getInt("BorderLine", 0))
                    .unusedTime(item.getInt("UnusedTime", 0))
                    .testCounts(item.getInt("TestCounts", 0))
                    .build();
            
        } catch (Exception e) {
            log.error("[AQKS考试详情] 失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 获取完整考试详情（通过TestID和CourseID）
     * 
     * 根据用户提供的API规范：
     * GET /api/MyScores?TestID=X&CourseID=Y 获取完整考试信息
     * 
     * @param courseId 课程ID
     * @param testId 考试ID
     * @param cookie 登录Cookie
     * @return 完整考试信息（包含学生信息、证书等）
     */
    public AqksExamInfo getExamFullDetail(String courseId, Integer testId, String cookie) {
        String url = BASE_URL + API_MY_SCORES + "?TestID=" + testId + "&CourseID=" + courseId;
        
        log.info("[AQKS完整考试详情] courseId={}, testId={}", courseId, testId);
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            
            log.debug("[AQKS完整考试详情] 响应: {}", body);
            
            if (StrUtil.isBlank(body)) {
                return null;
            }
            
            JSONArray arr = JSONUtil.parseArray(body);
            if (arr.isEmpty()) {
                return null;
            }
            
            JSONObject item = arr.getJSONObject(0);
            
            // 构建完整的考试信息对象
            return AqksExamInfo.builder()
                    // 基本信息
                    .courseId(item.getStr("CourseID"))
                    .courseName(item.getStr("CourseName"))
                    .testName(item.getStr("TestName"))
                    .testId(item.getInt("TestID", 0))
                    .testPaperId(item.getInt("TestPaperID", 0))
                    // 考试结果
                    .isPassed(item.getBool("IsPassed", false))
                    .isJoined(item.getBool("IsJoined", false))
                    .score(item.getInt("Score", 0))
                    .borderLine(item.getInt("BorderLine", 0))
                    .unusedTime(item.getInt("UnusedTime", 0))
                    .testCounts(parseTestCounts(item.getStr("TestCounts")))
                    // 完整详情字段
                    .departmentName(item.getStr("DepartmentName"))
                    .specialtyName(item.getStr("SpecialtyName"))
                    .grade(item.getInt("Grade", 0))
                    .className(item.getStr("ClassName"))
                    .menderName(item.getStr("MenderName"))
                    .menderCode(item.getStr("MenderCode"))
                    .testPaperName(item.getStr("TestPaperName"))
                    .endTime(item.getStr("EndTime"))
                    .onlineHours(item.getStr("OnlineHours"))
                    .isSubmit(item.getBool("IsSubmit", false))
                    .certificateId(item.getStr("CertificateId"))
                    .updateTime(item.getStr("UpdateTime"))
                    .build();
            
        } catch (Exception e) {
            log.error("[AQKS完整考试详情] 失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 一站式检查考试状态
     * 
     * 完整流程：
     * 1. 登录获取Cookie
     * 2. 获取考试列表获取CourseID
     * 3. 根据CourseID获取TestID
     * 4. 根据TestID和CourseID获取完整考试详情
     * 
     * @param username 学号
     * @param password 密码
     * @return 完整考试信息，如果没有考试或失败则返回null
     */
    public AqksExamInfo checkExamStatus(String username, String password) {
        log.info("[AQKS检查考试状态] username={}", username);
        
        try {
            // 第一步：登录
            AqksLoginResult loginResult = login(username, password);
            if (!loginResult.isSuccess()) {
                log.error("[AQKS检查考试状态] 登录失败: {}", loginResult.getErrorMessage());
                return null;
            }
            
            String cookie = loginResult.getServerCookie();
            
            // 第二步：获取考试列表，拿到CourseID
            List<AqksExamInfo> examList = getExamList(cookie);
            if (examList.isEmpty()) {
                log.info("[AQKS检查考试状态] 没有找到任何考试");
                return null;
            }
            
            // 取第一个考试
            AqksExamInfo firstExam = examList.get(0);
            String courseId = firstExam.getCourseId();
            
            log.info("[AQKS检查考试状态] 找到课程: courseId={}, name={}", 
                    courseId, firstExam.getCourseName());
            
            // 第三步：根据CourseID获取TestID
            AqksExamInfo examWithTestId = getExamDetail(courseId, cookie);
            if (examWithTestId == null || examWithTestId.getTestId() == null || examWithTestId.getTestId() == 0) {
                log.warn("[AQKS检查考试状态] 未能获取TestID");
                return firstExam;  // 返回基础信息
            }
            
            Integer testId = examWithTestId.getTestId();
            log.info("[AQKS检查考试状态] 获取到TestID: {}", testId);
            
            // 第四步：获取完整考试详情
            AqksExamInfo fullDetail = getExamFullDetail(courseId, testId, cookie);
            if (fullDetail != null) {
                log.info("[AQKS检查考试状态] 完整详情获取成功: score={}, isPassed={}, certificateId={}", 
                        fullDetail.getScore(), fullDetail.getIsPassed(), fullDetail.getCertificateId());
                return fullDetail;
            }
            
            return examWithTestId;  // 如果完整详情获取失败，返回有TestID的版本
            
        } catch (Exception e) {
            log.error("[AQKS检查考试状态] 失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解析考试次数字符串
     * TestCounts可能是字符串格式如"1"
     */
    private Integer parseTestCounts(String testCounts) {
        if (StrUtil.isBlank(testCounts)) {
            return 0;
        }
        try {
            return Integer.parseInt(testCounts.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    // ==================== 私有方法 ====================
    
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
     * 解析登录响应
     */
    private AqksLoginResult parseLoginResponse(String responseBody, String username) {
        if (StrUtil.isBlank(responseBody)) {
            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage("登录返回空响应")
                    .build();
        }
        
        try {
            JSONObject json = JSONUtil.parseObj(responseBody);
            
            // 检查是否有错误码 (Code=-9999 表示失败)
            if (json.containsKey("Code") && json.getInt("Code", 0) == -9999) {
                String errorMsg = json.getStr("Message", "账号或密码错误");
                return AqksLoginResult.builder()
                        .success(false)
                        .errorMessage(errorMsg)
                        .build();
            }
            
            // 检查是否登录成功 - 返回包含Token字段表示成功
            if (json.containsKey("Token") && json.getStr("Token") != null) {
                // 生成Cookie
                String serverCookie = generateServerCookie(json);
                String doubleCookie = generateDoubleCookie(json);
                
                return AqksLoginResult.builder()
                        .success(true)
                        .token(json.getStr("Token"))
                        .userId(String.valueOf(json.getInt("ID", 0)))
                        .userName(json.getStr("UserName", username))
                        .name(json.getStr("Name", username))
                        .departmentName(json.getStr("DepartmentName", ""))
                        .departmentId(json.getInt("DepartmentID", 0))
                        .specialtyName(json.getStr("SpecialtyName", ""))
                        .specialtyId(json.getInt("SpecialtyID", 0))
                        .grade(json.getInt("Grade", 0))
                        .classId(json.getInt("ClassID", 0))
                        .studyTimes(json.getStr("StudyTimes", "0"))
                        .minTimeMinute(json.getStr("MinTimeMinute", "0"))
                        .serverCookie(serverCookie)
                        .doubleCookie(doubleCookie)
                        .build();
            }
            
            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage("登录返回未知格式")
                    .build();
            
        } catch (Exception e) {
            log.error("[AQKS登录] 解析响应失败: {}", e.getMessage(), e);
            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage("解析登录响应失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 生成 LoginUserInfo_SYSAQ_Server Cookie值
     * 一次URL编码
     */
    private String generateServerCookie(JSONObject userInfo) {
        try {
            String jsonStr = userInfo.toString();
            return URLEncoder.encode(jsonStr, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("生成ServerCookie失败", e);
            return "";
        }
    }
    
    /**
     * 生成 LoginUserInfo_SYSAQ Cookie值
     * 双重URL编码
     */
    private String generateDoubleCookie(JSONObject userInfo) {
        try {
            String jsonStr = userInfo.toString();
            String once = URLEncoder.encode(jsonStr, StandardCharsets.UTF_8.name());
            return URLEncoder.encode(once, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("生成DoubleCookie失败", e);
            return "";
        }
    }
    
    /**
     * 构建带Cookie的请求头
     */
    private HttpHeaders buildCookieHeaders(String serverCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7");
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0");
        headers.set("Referer", BASE_URL + "/client_pc/exone.html");
        headers.set("X-Requested-With", "XMLHttpRequest");
        
        if (StrUtil.isNotBlank(serverCookie)) {
            headers.set("Cookie", "LoginUserInfo_SYSAQ_Server=" + serverCookie);
        }
        
        return headers;
    }
}
