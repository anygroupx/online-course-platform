package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.AnnouncementCreateDTO;
import com.course.platform.domain.dto.AnnouncementUpdateDTO;
import com.course.platform.domain.vo.AnnouncementVO;
import com.course.platform.application.service.support.AnnouncementService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公告管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/announcement")
@RequiredArgsConstructor
@Tag(name = "公告管理", description = "公告发布和管理相关接口")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final OperationLogService operationLogService;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('announcement:create')")
    @Operation(summary = "创建公告", description = "管理员创建新公告")
    public Result<Long> createAnnouncement(@RequestBody AnnouncementCreateDTO createDTO) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long announcementId = announcementService.createAnnouncement(createDTO, userId);
        operationLogService.log(userId, "创建公告",
                "创建公告：" + createDTO.getTitle(), null, null);
        return Result.success(announcementId);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('announcement:update')")
    @Operation(summary = "更新公告", description = "管理员更新公告信息")
    public Result<Boolean> updateAnnouncement(@RequestBody AnnouncementUpdateDTO updateDTO) {
        Long userId = SecurityUtils.getCurrentUserId();
        Boolean success = announcementService.updateAnnouncement(updateDTO, userId);
        operationLogService.log(userId, "更新公告",
                "更新公告ID：" + updateDTO.getId(), null, null);
        return Result.success(success);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('announcement:delete')")
    @Operation(summary = "删除公告", description = "管理员删除公告")
    public Result<Boolean> deleteAnnouncement(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Boolean success = announcementService.deleteAnnouncement(id, userId);
        operationLogService.log(userId, "删除公告",
                "删除公告ID：" + id, null, null);
        return Result.success(success);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('announcement:publish')")
    @Operation(summary = "发布公告", description = "管理员发布公告")
    public Result<Boolean> publishAnnouncement(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Boolean success = announcementService.publishAnnouncement(id, userId);
        operationLogService.log(userId, "发布公告",
                "发布公告ID：" + id, null, null);
        return Result.success(success);
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("hasAuthority('announcement:publish')")
    @Operation(summary = "下线公告", description = "管理员下线公告")
    public Result<Boolean> offlineAnnouncement(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Boolean success = announcementService.offlineAnnouncement(id, userId);
        operationLogService.log(userId, "下线公告",
                "下线公告ID：" + id, null, null);
        return Result.success(success);
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('announcement:update')")
    @Operation(summary = "分页查询公告列表", description = "管理员分页查询公告列表")
    public Result<IPage<AnnouncementVO>> getAnnouncementPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "公告类型") @RequestParam(required = false) Integer type,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "标题关键词") @RequestParam(required = false) String title) {
        Page<AnnouncementVO> page = new Page<>(current, size);
        IPage<AnnouncementVO> result = announcementService.getAnnouncementPage(page, type, status, title);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询公告详情", description = "根据ID查询公告详情")
    public Result<AnnouncementVO> getAnnouncementById(@PathVariable Long id) {
        AnnouncementVO announcement = announcementService.getAnnouncementById(id);
        return Result.success(announcement);
    }

    @GetMapping("/latest")
    @Operation(summary = "查询最新公告列表", description = "用户端查询最新公告列表")
    public Result<List<AnnouncementVO>> getLatestAnnouncements(
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "5") Integer limit) {
        List<AnnouncementVO> announcements = announcementService.getLatestAnnouncements(limit);
        return Result.success(announcements);
    }

    @GetMapping("/system")
    @Operation(summary = "查询系统公告", description = "用户端查询系统公告，用于首次登录弹窗")
    public Result<AnnouncementVO> getSystemAnnouncement() {
        AnnouncementVO announcement = announcementService.getSystemAnnouncement();
        return Result.success(announcement);
    }

    @GetMapping("/top")
    @Operation(summary = "查询置顶公告", description = "用户端查询置顶公告")
    public Result<List<AnnouncementVO>> getTopAnnouncements() {
        List<AnnouncementVO> announcements = announcementService.getTopAnnouncements();
        return Result.success(announcements);
    }
}
