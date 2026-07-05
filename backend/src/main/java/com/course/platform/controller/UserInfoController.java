package com.course.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.shared.constant.Constants;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.Result;
import com.course.platform.shared.result.ResultCode;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.UserInfoResponse;
import com.course.platform.mapper.CourseOrderMapper;
import com.course.platform.mapper.SystemConfigMapper;
import com.course.platform.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 用户信息控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "用户信息", description = "获取用户详细信息接口")
@RestController
@RequestMapping("/user/info")
@RequiredArgsConstructor
public class UserInfoController {

    private final UserMapper userMapper;
    private final CourseOrderMapper courseOrderMapper;
    private final SystemConfigMapper systemConfigMapper;

    /**
     * 获取当前用户完整信息
     */
    @Operation(summary = "获取用户完整信息", description = "获取当前用户的详细信息和统计数据")
    @GetMapping
    public Result<UserInfoResponse> getUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        // 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 查询订单总数
        Long totalOrders = courseOrderMapper.selectCount(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getUserId, userId));

        // 查询上级信息
        String parentUsername = null;
        String parentNotice = null;
        if (!Constants.ROOT_PARENT_ID.equals(user.getParentId())) {
            User parent = userMapper.selectById(user.getParentId());
            if (parent != null) {
                parentUsername = parent.getUsername();
                parentNotice = parent.getNotice();
            }
        }

        // 查询代理统计
        UserInfoResponse.AgentStatistics agentStats = getAgentStatistics(userId);

        // 构建响应
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .balance(user.getBalance())
                .rate(user.getRate())
                .apiKey(user.getApiKey())
                .inviteCode(user.getInviteCode())
                .inviteRate(user.getInviteRate())
                .totalOrders(totalOrders)
                .totalRecharge(user.getTotalRecharge())
                .parentUsername(parentUsername)
                .parentNotice(parentNotice)
                .systemNotice("欢迎使用在线网课平台！")
                .agentStats(agentStats)
                .build();

        return Result.success(response);
    }

    /**
     * 获取代理统计数据
     */
    private UserInfoResponse.AgentStatistics getAgentStatistics(Long userId) {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 代理总数
        Long totalAgents = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getParentId, userId));

        // 今日注册
        Long todayRegistered = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getParentId, userId)
                .between(User::getCreateTime, todayStart, todayEnd));

        // 今日登录
        Long todayLogin = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getParentId, userId)
                .between(User::getLastLoginTime, todayStart, todayEnd));

        // 今日下单
        Long todayOrders = courseOrderMapper.selectCount(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getUserId, userId)
                .between(CourseOrder::getCreateTime, todayStart, todayEnd));

        return UserInfoResponse.AgentStatistics.builder()
                .totalAgents(totalAgents)
                .todayRegistered(todayRegistered)
                .todayLogin(todayLogin)
                .todayOrders(todayOrders)
                .build();
    }
}

