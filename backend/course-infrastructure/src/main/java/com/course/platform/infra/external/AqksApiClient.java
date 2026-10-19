package com.course.platform.infra.external;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.course.platform.domain.dto.aqks.AqksExamInfo;
import com.course.platform.domain.dto.aqks.AqksLoginResult;
import com.course.platform.infra.http.OutboundPolicyRegistry;
import com.course.platform.infra.http.SafeHttpClient;
import com.course.platform.infra.http.SafeHttpException;
import com.course.platform.infra.http.SafeHttpResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
     * API端点
     */
    private static final String API_LOGIN = "/api/MyUserInfo";
    private static final String API_STUDY_TIME_SET = "/api/LoginTimesSet";
    private static final String API_STUDY_TIME_GET = "/api/LoginTimesGet";
    private static final String API_MY_SCORES = "/api/MyScores";
    
    private final SafeHttpClient safeHttpClient;
    private final OutboundPolicyRegistry policies;
    private final AqksTransportProperties transport;

    public AqksApiClient(SafeHttpClient safeHttpClient, OutboundPolicyRegistry policies,
                         AqksTransportProperties transport) {
        this.safeHttpClient = safeHttpClient;
        this.policies = policies;
        this.transport = transport;
    }
    
    /**
     * 登录AQKS平台
     * 
     * @param username 学号
     * @param password 密码
     * @return 登录结果
     */
    public AqksLoginResult login(String username, String password) {
        URI url = aqksUri(API_LOGIN, Map.of("UserName", username, "isBackground", false));
        
        log.debug("[AQKS] 请求处理");
        
        try {
            // The browser client sends SHA256(password) as a hex string, and the upstream
            // compares that digest. Base64 was never accepted; see encodePassword below.
            String encodedPassword = encodePassword(password);
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.setAccept(Collections.singletonList(MediaType.ALL));
            headers.set("Connection", "keep-alive");
            // Transport credential for the campus WebVPN proxy; blank on a direct deployment.
            headers.set("Cookie", buildCookieValue(null));
            
            // Body is the JSON-encoded digest string, i.e. a quoted hex value.
            SafeHttpResponse response = safeHttpClient.postJson(url, singleHeaders(headers),
                    "\"" + encodedPassword + "\"", policies.aqks());

            // The upstream reports business rejections (unknown user, wrong password, lockout)
            // with a non-2xx status while still returning a JSON body that carries the real
            // reason, so the body decides the outcome. The status is only consulted when the
            // body is not a JSON object, i.e. when no business answer was actually returned.
            String responseBody = response.body();
            if (isJsonObject(responseBody)) {
                AqksLoginResult parsed = parseLoginResponse(responseBody, username);
                log.info("[AQKS登录] 响应状态: {}, success={}", response.statusCode(), parsed.isSuccess());
                return parsed;
            }
            if (!response.isSuccessful()) {
                throw new SafeHttpException(SafeHttpException.Reason.HTTP_ERROR, response.statusCode());
            }
            log.info("[AQKS登录] 响应状态: {}", response.statusCode());
            return parseLoginResponse(responseBody, username);

        } catch (SafeHttpException e) {
            log.error("[AQKS登录] 登录失败: reason={}, status={}", e.getReason(), e.getStatusCode());
            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage(describeTransportFailure(e))
                    .build();
        } catch (Exception e) {
            log.error("[AQKS登录] 登录失败: {}", e.getClass().getSimpleName());
            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage("登录服务暂不可用")
                    .build();
        }
    }

    /**
     * Whether the body is a JSON object, i.e. a business answer rather than a gateway page.
     *
     * <p>An HTML error page must not be handed to the JSON parser: doing so turns a clear
     * transport failure into a misleading "unknown format" answer.</p>
     */
    private boolean isJsonObject(String body) {
        if (StrUtil.isBlank(body)) {
            return false;
        }
        String trimmed = body.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    /**
     * Turns a classified transport failure into an operator-actionable message.
     *
     * <p>Callers surface this string to users, so it must name the failing layer without
     * leaking the target URL, credentials or response content. A single generic message for
     * every cause is what made this integration opaque in the first place.</p>
     */
    private String describeTransportFailure(SafeHttpException e) {
        return switch (e.getReason()) {
            case TIMEOUT -> "登录服务响应超时";
            case DNS_FAILURE -> "无法解析登录服务地址";
            case TLS_FAILURE -> "登录服务 TLS 握手失败";
            case NETWORK_FAILURE -> "无法连接登录服务";
            case BLOCKED_DESTINATION, PRIVATE_ADDRESS -> "登录服务地址被出站策略拦截";
            case REDIRECT_BLOCKED -> "登录服务返回了不允许的重定向";
            case RESPONSE_TOO_LARGE -> "登录服务响应体超出限制";
            case PROVIDER_NOT_ACTIVE -> "登录服务未启用";
            case HTTP_ERROR, INVALID_RESPONSE -> e.getStatusCode() == null
                    ? "登录服务返回异常响应"
                    : "登录服务返回 HTTP " + e.getStatusCode();
        };
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
        if (StrUtil.isBlank(userId) || deltaSeconds <= 0 || StrUtil.isBlank(cookie)) {
            log.warn("[AQKS刷时长] 请求参数非法: userIdPresent={}, delta={}秒, cookiePresent={}",
                    StrUtil.isNotBlank(userId), deltaSeconds, StrUtil.isNotBlank(cookie));
            return false;
        }

        URI url = aqksUri(API_STUDY_TIME_SET, Map.of("UserID", userId, "StudyTimes", deltaSeconds));

        log.info("[AQKS刷时长] userId={}, delta={}秒", userId, deltaSeconds);

        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            SafeHttpResponse response = safeHttpClient.postJson(url, singleHeaders(headers), String.valueOf(deltaSeconds), policies.aqks());
            String body = requireSuccess(response);
            boolean success = isStudyTimeUpdateSuccessful(body);
            log.info("[AQKS刷时长] 响应状态: {}, success={}", response.statusCode(), success);
            return success;

        } catch (SafeHttpException e) {
            log.error("[AQKS刷时长] 失败: reason={}, status={}", e.getReason(), e.getStatusCode());
            return false;
        } catch (Exception e) {
            log.error("[AQKS刷时长] 失败: {}", e.getClass().getSimpleName());
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
        log.debug("[AQKS] 请求处理");
        
        try {
            AqksLoginResult result = login(username, password);
            if (result.isSuccess() && result.getStudyTimes() != null) {
                return Integer.parseInt(result.getStudyTimes());
            }
            return -1;
        } catch (Exception e) {
            log.error("[AQKS读时长] 登录验证失败: {}", e.getClass().getSimpleName());
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
        URI url = aqksUri(API_STUDY_TIME_GET, Map.of("UserID", userId));
        
        log.debug("[AQKS] 请求处理");
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            SafeHttpResponse response = safeHttpClient.get(url, singleHeaders(headers), policies.aqks());
            String body = requireSuccess(response);
            
            log.debug("[AQKS] 请求处理");
            
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
            log.error("[AQKS读时长] 失败: {}", e.getClass().getSimpleName());
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
        URI url = aqksUri(API_MY_SCORES, Map.of());
        
        log.debug("[AQKS考试列表] 请求");
        
        List<AqksExamInfo> examList = new ArrayList<>();
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            SafeHttpResponse response = safeHttpClient.get(url, singleHeaders(headers), policies.aqks());
            String body = requireSuccess(response);
            
            log.debug("[AQKS] 请求处理");
            
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
            log.error("[AQKS考试列表] 失败: {}", e.getClass().getSimpleName());
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
        URI url = aqksUri(API_MY_SCORES, Map.of("CourseID", courseId));
        
        log.debug("[AQKS考试详情] courseId={}", courseId);
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            SafeHttpResponse response = safeHttpClient.get(url, singleHeaders(headers), policies.aqks());
            String body = requireSuccess(response);
            
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
            log.error("[AQKS考试详情] 失败: {}", e.getClass().getSimpleName());
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
        URI url = aqksUri(API_MY_SCORES, Map.of("TestID", testId, "CourseID", courseId));
        
        log.info("[AQKS完整考试详情] courseId={}, testId={}", courseId, testId);
        
        try {
            HttpHeaders headers = buildCookieHeaders(cookie);
            SafeHttpResponse response = safeHttpClient.get(url, singleHeaders(headers), policies.aqks());
            String body = requireSuccess(response);
            
            log.debug("[AQKS] 请求处理");
            
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
            log.error("[AQKS完整考试详情] 失败: {}", e.getClass().getSimpleName());
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
        log.debug("[AQKS] 请求处理");
        
        try {
            // 第一步：登录
            AqksLoginResult loginResult = login(username, password);
            if (!loginResult.isSuccess()) {
                log.warn("[AQKS检查考试状态] 登录失败");
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
                log.debug("[AQKS检查考试状态] 完整详情获取成功");
                return fullDetail;
            }
            
            return examWithTestId;  // 如果完整详情获取失败，返回有TestID的版本
            
        } catch (Exception e) {
            log.error("[AQKS检查考试状态] 失败: {}", e.getClass().getSimpleName());
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
     * Encodes the password exactly as the browser client does.
     *
     * <p>The upstream compares against {@code SHA256(password)} rendered as a lowercase hex
     * string (the frontend's {@code compileStr()}); it does not accept Base64. Encoding it any
     * other way makes every credential look wrong, which is indistinguishable from a genuinely
     * bad password and hides the real cause.</p>
     */
    private String encodePassword(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; reaching here means a broken runtime.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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

            // Check success first: a response carrying a Token is a logged-in user regardless of
            // any Code field the upstream may also include.
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

            // Any non-zero Code is a business rejection, not just -9999. The upstream reports
            // wrong credentials as -2 and lockout as -99, and it puts the operator-readable
            // reason in Message; treating only -9999 as a rejection made every other failure
            // surface as "unknown format".
            if (json.containsKey("Code") && json.getInt("Code", 0) != 0) {
                String errorMsg = json.getStr("Message", "账号或密码错误");
                return AqksLoginResult.builder()
                        .success(false)
                        .errorMessage(errorMsg)
                        .build();
            }

            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage("登录返回未知格式")
                    .build();
            
        } catch (Exception e) {
            log.error("[AQKS登录] 解析响应失败: {}", e.getClass().getSimpleName());
            return AqksLoginResult.builder()
                    .success(false)
                    .errorMessage("解析登录响应失败")
                    .build();
        }
    }

    private boolean isStudyTimeUpdateSuccessful(String body) {
        if (StrUtil.isBlank(body) || "true".equalsIgnoreCase(body.trim())) {
            return true;
        }
        if (!body.trim().startsWith("{")) {
            return false;
        }
        JSONObject json = JSONUtil.parseObj(body);
        if (json.containsKey("success")) {
            return Boolean.TRUE.equals(json.getBool("success"));
        }
        if (json.containsKey("Success")) {
            return Boolean.TRUE.equals(json.getBool("Success"));
        }
        return false;
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
    private URI aqksUri(String path, Map<String, ?> query) {
        HttpUrl.Builder builder = java.util.Objects.requireNonNull(
                HttpUrl.parse(transport.getBaseUrl() + path)).newBuilder();
        query.forEach((key, value) -> {
            if (key != null && value != null) builder.addQueryParameter(key, String.valueOf(value));
        });
        return builder.build().uri();
    }

    private String requireSuccess(SafeHttpResponse response) {
        if (!response.isSuccessful()) {
            // Carry the status: without it a rejected response is indistinguishable in the
            // logs from a dead network.
            throw new SafeHttpException(SafeHttpException.Reason.HTTP_ERROR, response.statusCode());
        }
        return response.body();
    }

    private java.util.Map<String, String> singleHeaders(HttpHeaders headers) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        headers.forEach((key, list) -> {
            if (key != null && list != null && !list.isEmpty()) values.put(key, list.get(0));
        });
        return values;
    }

    private HttpHeaders buildCookieHeaders(String serverCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7");
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0");
        headers.set("Referer", transport.getBaseUrl() + transport.getRefererPath());
        headers.set("X-Requested-With", "XMLHttpRequest");
        
        headers.set("Cookie", buildCookieValue(serverCookie));
        
        return headers;
    }

    /**
     * Builds the Cookie header for a request to the AQKS origin.
     *
     * <p>Two credentials travel together and must not be confused:</p>
     * <ul>
     *   <li>{@code webvpn-token} — a transport credential for the campus WebVPN reverse
     *       proxy. It is service-wide (identical for every student) and only present when
     *       the deployment tunnels through WebVPN.</li>
     *   <li>{@code LoginUserInfo_SYSAQ_Server} — the per-student AQKS application session
     *       obtained from {@link #login(String, String)}.</li>
     * </ul>
     *
     * @param serverCookie per-student AQKS session cookie; may be blank for the login call
     */
    private String buildCookieValue(String serverCookie) {
        StringBuilder cookie = new StringBuilder();
        if (transport.usesWebVpn()) {
            cookie.append("webvpn-token=").append(transport.getWebVpnToken());
        }
        if (StrUtil.isNotBlank(serverCookie)) {
            if (cookie.length() > 0) cookie.append("; ");
            cookie.append("LoginUserInfo_SYSAQ_Server=").append(serverCookie);
        }
        return cookie.toString();
    }
}
