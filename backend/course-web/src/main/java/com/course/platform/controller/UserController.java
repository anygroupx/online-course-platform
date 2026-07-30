package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.RechargeRequest;
import com.course.platform.domain.dto.UserCreateRequest;
import com.course.platform.domain.dto.UserUpdateRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.UserVO;
import com.course.platform.application.service.user.UserService;
import com.course.platform.security.SensitiveDataMasker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器（响应仅返回 UserVO）
 */
@Tag(name = "用户管理", description = "用户CRUD、代理管理、充值等接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "创建用户", description = "创建新用户（代理开户）")
    @PostMapping
    public Result<Long> createUser(@Valid @RequestBody UserCreateRequest request,
                                    Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        Long userId = userService.createUser(request, operatorId);
        return Result.success("用户创建成功", userId);
    }

    @Operation(summary = "更新用户信息", description = "更新用户基本信息")
    @PutMapping
    public Result<Void> updateUser(@Valid @RequestBody UserUpdateRequest request,
                                    Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        userService.updateUser(request, operatorId);
        return Result.success("用户信息更新成功");
    }

    @Operation(summary = "查询用户列表", description = "分页查询用户列表")
    @GetMapping
    public Result<IPage<UserVO>> queryUsers(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        IPage<User> result = userService.queryUsers(keyword, status, page, pageSize, operatorId);
        Page<UserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(SensitiveDataMasker::toUserVO).toList());
        return Result.success(voPage);
    }

    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id,
                                 Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        User user = userService.getUserById(id, operatorId);
        return Result.success(SensitiveDataMasker.toUserVO(user));
    }

    @Operation(summary = "充值", description = "给下级代理充值")
    @PostMapping("/recharge")
    public Result<Void> recharge(@Valid @RequestBody RechargeRequest request,
                                  Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        userService.recharge(request, operatorId);
        return Result.success("充值成功");
    }

    @Operation(summary = "修改密码", description = "修改当前用户密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestParam String oldPassword,
                                        @RequestParam String newPassword,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, oldPassword, newPassword);
        return Result.success("密码修改成功");
    }

    @Operation(summary = "重置密码", description = "重置下级用户密码")
    @PostMapping("/{id}/reset-password")
    public Result<String> resetPassword(@PathVariable Long id,
                                         Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        String newPassword = userService.resetPassword(id, operatorId);
        return Result.success("密码重置成功", newPassword);
    }

    @Operation(summary = "禁用/启用用户", description = "修改用户状态")
    @PostMapping("/{id}/status")
    public Result<Void> changeUserStatus(@PathVariable Long id,
                                          @RequestParam Integer status,
                                          Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        userService.changeUserStatus(id, status, operatorId);
        return Result.success("用户状态修改成功");
    }
}
