package com.course.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.common.util.PublicUidUtil;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.order.CourseOrderService;
import com.course.platform.application.service.course.CourseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部API控制器
 * 供第三方系统调用的API接口
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Tag(name = "外部API", description = "供第三方系统调用的API接口")
@RestController
@RequestMapping("/external")
@RequiredArgsConstructor
public class ExternalApiController {

    private final UserMapper userMapper;
    private final CourseOrderMapper courseOrderMapper;
    private final CoursePlatformMapper coursePlatformMapper;
    private final CourseOrderService courseOrderService;
    private final CourseQueryService courseQueryService;

    /**
     * 验证API密钥
     */
    private User validateApiKey(String uid, String key, String apiKey, String requiredScope) {
        // api_key is the documented name; keep key for existing PHP/29 integrations.
        if (key != null && !key.isBlank() && apiKey != null && !apiKey.isBlank() && !key.equals(apiKey)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "key 与 api_key 不能使用不同的值");
        }
        String credential = key != null && !key.isBlank() ? key : apiKey;
        if (credential == null || credential.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "UID和API密钥不能为空");
        }

        if (!PublicUidUtil.isValid(uid)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "UID必须为用户 UUID");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUid, PublicUidUtil.normalize(uid))
                .last("LIMIT 1"));
        String providedHash = TokenHashUtil.sha256(credential);
        boolean matched = user != null && user.getApiKeyHash() != null && !user.getApiKeyHash().isBlank()
                && MessageDigest.isEqual(
                providedHash.getBytes(StandardCharsets.US_ASCII),
                user.getApiKeyHash().getBytes(StandardCharsets.US_ASCII));
        if (!matched || (user.getApiKeyExpireTime() != null
                && !user.getApiKeyExpireTime().isAfter(LocalDateTime.now()))) {
            throw new BusinessException(ResultCode.API_KEY_INVALID);
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        boolean scopeAllowed = user.getApiKeyScopes() != null
                && java.util.Arrays.stream(user.getApiKeyScopes().split(","))
                .map(String::trim)
                .anyMatch(requiredScope::equals);
        if (!scopeAllowed) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        // The business layer also enforces object authorization. An APIKey-validated caller
        // needs an owner principal for that check, but must NOT inherit web/admin RBAC rights.
        // SecurityContextHolderFilter clears this request-local context after the response.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.getId(), null,
                List.of(new SimpleGrantedAuthority("api:" + requiredScope))));
        SecurityContextHolder.setContext(context);
        return user;
    }

    /**
     * 查询余额
     */
    @Operation(summary = "查询余额", description = "通过UID和API密钥查询账户余额")
    @PostMapping("/getmoney")
    public Result<Map<String, Object>> getMoney(@RequestParam String uid,
            @RequestParam(required = false) String key,
            @RequestParam(name = "api_key", required = false) String apiKey) {
        User user = validateApiKey(uid, key, apiKey, "balance:read");

        Map<String, Object> data = new HashMap<>();
        data.put("money", user.getBalance());

        return Result.success("查询成功", data);
    }

    /**
     * 查单
     */
    @Operation(summary = "查单", description = "根据学生账号查询订单（需API密钥验证）")
    @PostMapping("/chadan")
    public Result<List<Map<String, Object>>> queryOrdersByUsername(
            @RequestParam String uid,
            @RequestParam(required = false) String key,
            @RequestParam(name = "api_key", required = false) String apiKey,
            @RequestParam String username) {
        // 验证API密钥并获取用户
        User user = validateApiKey(uid, key, apiKey, "orders:read");

        if (username == null || username.isBlank()) {
            throw new BusinessException("账号不能为空");
        }

        // 查询订单时添加userId归属校验
        List<CourseOrder> orders = courseOrderMapper.selectList(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getUserId, user.getId())
                .eq(CourseOrder::getStudentAccount, username)
                .orderByDesc(CourseOrder::getCreateTime));

        if (orders.isEmpty()) {
            throw new BusinessException("未查到该账号的下单信息");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseOrder order : orders) {
            Map<String, Object> map = new HashMap<>();
            // 使用orderNo代替id，移除数据库ID暴露
            map.put("orderNo", order.getOrderNo());
            map.put("ptname", order.getPlatformName());
            map.put("school", order.getSchoolName());
            map.put("name", order.getStudentName());
            map.put("user", order.getStudentAccount());
            map.put("kcname", order.getCourseName());
            map.put("addtime", order.getCreateTime());
            map.put("courseStartTime", order.getCourseStartTime());
            map.put("courseEndTime", order.getCourseEndTime());
            map.put("examStartTime", order.getExamStartTime());
            map.put("examEndTime", order.getExamEndTime());
            map.put("status", getStatusText(order.getOrderStatus()));
            map.put("process", order.getProgress());
            map.put("remarks", order.getRemarks());
            result.add(map);
        }

        return Result.success(result);
    }

    /**
     * 补单
     */
    @Operation(summary = "补单", description = "根据订单编号补单（需API密钥验证）")
    @PostMapping("/budan")
    public Result<Map<String, Object>> retryOrderExternal(
            @RequestParam String uid,
            @RequestParam(required = false) String key,
            @RequestParam(name = "api_key", required = false) String apiKey,
            @RequestParam String orderNo) {
        // 验证API密钥并获取用户
        User user = validateApiKey(uid, key, apiKey, "orders:write");

        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException("订单编号不能为空");
        }

        // 根据orderNo和userId查询订单，确保归属校验
        CourseOrder order = courseOrderMapper.selectOne(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getOrderNo, orderNo)
                .eq(CourseOrder::getUserId, user.getId()));

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 检查补单次数
        if (order.getRetryCount() != null && order.getRetryCount() >= 5) {
            throw new BusinessException("该订单补刷已超过5次，请联系客服");
        }

        courseOrderService.retryOrder(order.getId(), user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        return Result.success("补单成功", data);
    }

    /**
     * 单下单
     */
    @Operation(summary = "单下单", description = "通过API下单")
    @PostMapping("/add")
    public Result<Map<String, Object>> createOrderExternal(@RequestParam String uid,
            @RequestParam(required = false) String key,
            @RequestParam(name = "api_key", required = false) String apiKey,
            @RequestParam String platform,
            @RequestParam(required = false) String school,
            @RequestParam String user,
            @RequestParam String pass,
            @RequestParam(required = false) String kcid,
            @RequestParam String kcname) {
        // 验证API密钥
        User userObj = validateApiKey(uid, key, apiKey, "orders:write");

        if (user.isBlank() || pass.isBlank() || kcname.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "学生账号、密码和课程名称不能为空");
        }
        // 创建订单请求
        OrderCreateRequest request = new OrderCreateRequest();
        request.setPlatformId(parsePlatformId(platform));
        request.setSchoolName(school);
        request.setStudentAccount(user);
        request.setStudentPassword(pass);
        request.setCourseId(kcid);
        request.setCourseName(kcname);

        // 创建订单
        Long orderId = courseOrderService.createOrder(request, userObj.getId());

        // 查询订单获取orderNo
        CourseOrder order = courseOrderMapper.selectById(orderId);

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        return Result.success("提交成功", data);
    }

    /**
     * 查课（供第三方调用）
     */
    @Operation(summary = "查课", description = "通过API查询学生的课程列表")
    @PostMapping("/query-courses")
    public Result<CourseInfoResponse> queryCoursesExternal(@RequestParam String uid,
                      @RequestParam(required = false) String key,
            @RequestParam(name = "api_key", required = false) String apiKey,
                         @RequestParam String platform,
                      @RequestParam(required = false) String school,
                         @RequestParam String user,
                         @RequestParam String pass) {
        // 验证API密钥
        User authenticatedUser = validateApiKey(uid, key, apiKey, "platforms:read");

        if (user.isBlank() || pass.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "学生账号和密码不能为空");
        }
        // 构建查课请求
        QueryCourseRequest request = new QueryCourseRequest();
        request.setPlatformId(parsePlatformId(platform));
        request.setSchoolName(school);
        request.setStudentAccount(user);
        request.setStudentPassword(pass);

        // 调用查课服务
        CourseInfoResponse response = courseQueryService.queryCourses(request, authenticatedUser.getId());

        return Result.success("查询成功", response);
    }

    /**
     * 查询订单进度
     */
    @Operation(summary = "查询订单进度", description = "根据订单编号查询订单进度（需API密钥验证）")
    @PostMapping("/query-progress")
    public Result<Map<String, Object>> queryProgressExternal(@RequestParam String uid,
                         @RequestParam(required = false) String key,
            @RequestParam(name = "api_key", required = false) String apiKey,
                            @RequestParam String orderNo) {
        // 验证API密钥并获取用户
        User user = validateApiKey(uid, key, apiKey, "orders:read");

        // 根据orderNo和userId查询订单，确保归属校验
        CourseOrder order = courseOrderMapper.selectOne(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getOrderNo, orderNo)
                .eq(CourseOrder::getUserId, user.getId()));

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 构建响应，移除orderId字段
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("platformName", order.getPlatformName());
        data.put("studentAccount", order.getStudentAccount());
        data.put("courseName", order.getCourseName());
        data.put("orderStatus", order.getOrderStatus());
        data.put("orderStatusText", getStatusText(order.getOrderStatus()));
        data.put("dockStatus", order.getDockStatus());
        data.put("progress", order.getProgress());
        data.put("remarks", order.getRemarks());
        data.put("createTime", order.getCreateTime());
        data.put("updateTime", order.getUpdateTime());

        return Result.success("查询成功", data);
    }

    /**
     * 获取可用平台列表
     */
    @Operation(summary = "获取平台列表", description = "获取所有可用的课程平台列表")
    @PostMapping("/get-platforms")
    public Result<List<Map<String, Object>>> getPlatformsExternal(@RequestParam String uid,
                              @RequestParam(required = false) String key,
            @RequestParam(name = "api_key", required = false) String apiKey) {
        // 验证API密钥
        validateApiKey(uid, key, apiKey, "platforms:read");

        // 查询所有在线平台
        List<CoursePlatform> platforms = coursePlatformMapper.selectList(new LambdaQueryWrapper<CoursePlatform>()
                .eq(CoursePlatform::getStatus, 1)
                .orderByAsc(CoursePlatform::getSortOrder));

        // 构建响应
        List<Map<String, Object>> result = new ArrayList<>();
        for (CoursePlatform platform : platforms) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", platform.getId());
            map.put("name", platform.getName());
            map.put("description", platform.getDescription());
            map.put("price", platform.getBasePrice());
            result.add(map);
        }


        return Result.success(result);
    }

    private Long parsePlatformId(String platform) {
        try {
            long id = Long.parseLong(platform);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // Do not echo caller-controlled parameters (which may contain credentials).
        }
        throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "平台ID必须为正整数");
    }

    /**
     * 获取状态文本
     * Source: 使用SystemVariableCache动态获取状态名称
     */
    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        return SystemVariableCache.getStatusName("order_status", String.valueOf(status));
    }
}
