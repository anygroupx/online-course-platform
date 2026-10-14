package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 通知拥有者来自当前会话，不能通过客户端参数指定。 */
@RestController
@RequestMapping("/course-private-operations/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CoursePrivateNotificationController {
    private final CoursePrivateNotificationService service;
    @GetMapping public Result<Map<String,Object>> inbox(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.inbox(SecurityUtils.getCurrentUserId(),page,pageSize));}
    @PostMapping("/{id}/read") public Result<Void> read(@PathVariable Long id){service.markRead(SecurityUtils.getCurrentUserId(),id);return Result.success();}
}
