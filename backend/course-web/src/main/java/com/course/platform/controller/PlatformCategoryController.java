package com.course.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.PlatformCategory;
import com.course.platform.infra.persistence.mapper.PlatformCategoryMapper;
import com.course.platform.application.service.course.CoursePlatformService;
import com.course.platform.application.service.support.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/platform-categories")
@RequiredArgsConstructor
public class PlatformCategoryController {

    private final PlatformCategoryMapper platformCategoryMapper;
    private final CoursePlatformService coursePlatformService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<Page<PlatformCategory>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<PlatformCategory> pageResult = platformCategoryMapper.selectPage(
                new Page<>(page, pageSize),
                new QueryWrapper<PlatformCategory>().orderByAsc("sort_order")
        );
        return Result.success(pageResult);
    }

    @PostMapping
    public Result<Void> create(@RequestBody PlatformCategory category) {
        platformCategoryMapper.insert(category);
        
        // 记录操作日志（管理员操作）
        operationLogService.log(1L, "创建平台分类", 
                "创建平台分类：" + category.getName(), null, null);
        
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody PlatformCategory category) {
        platformCategoryMapper.updateById(category);
        
        // 记录操作日志（管理员操作）
        operationLogService.log(1L, "更新平台分类", 
                "更新平台分类：" + category.getName(), null, null);
        
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        platformCategoryMapper.deleteById(id);
        
        // 记录操作日志（管理员操作）
        operationLogService.log(1L, "删除平台分类", 
                "删除平台分类ID：" + id, null, null);
        
        return Result.success();
    }

    /**
     * 方案A：批量删除某分类下的所有课程平台（独立接口）
     */
    @DeleteMapping("/{id}/platforms")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> deletePlatformsByCategoryId(@PathVariable Long id) {
        int deletedCount = coursePlatformService.deletePlatformsByCategoryId(id);
        
        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("categoryId", id);
        
        return Result.success("成功删除 " + deletedCount + " 个课程平台", result);
    }

    /**
     * 方案B：级联删除分类及其下所有课程平台
     */
    @DeleteMapping("/{id}/cascade")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> deleteCascade(@PathVariable Long id) {
        // 先删除该分类下的所有课程平台
        int deletedPlatformsCount = coursePlatformService.deletePlatformsByCategoryId(id);
        
        // 再删除分类本身
        platformCategoryMapper.deleteById(id);
        
        Map<String, Object> result = new HashMap<>();
        result.put("deletedPlatformsCount", deletedPlatformsCount);
        result.put("deletedCategoryId", id);
        
        return Result.success("成功删除分类及其 " + deletedPlatformsCount + " 个课程平台", result);
    }
}
