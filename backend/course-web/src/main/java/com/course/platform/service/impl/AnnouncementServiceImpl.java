package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.domain.dto.AnnouncementCreateDTO;
import com.course.platform.domain.dto.AnnouncementUpdateDTO;
import com.course.platform.domain.entity.Announcement;
import com.course.platform.domain.vo.AnnouncementVO;
import com.course.platform.infra.persistence.mapper.AnnouncementMapper;
import com.course.platform.application.service.support.AnnouncementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAnnouncement(AnnouncementCreateDTO createDTO, Long userId) {
        log.info("创建公告，标题：{}，创建人：{}", createDTO.getTitle(), userId);
        
        Announcement announcement = new Announcement();
        BeanUtils.copyProperties(createDTO, announcement);
        
        // 设置默认值
        if (announcement.getPublishTime() == null) {
            announcement.setPublishTime(LocalDateTime.now());
        }
        announcement.setStatus(1); // 默认已发布
        announcement.setCreateBy(userId);
        
        announcementMapper.insert(announcement);
        
        log.info("公告创建成功，ID：{}", announcement.getId());
        return announcement.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateAnnouncement(AnnouncementUpdateDTO updateDTO, Long userId) {
        log.info("更新公告，ID：{}，操作人：{}", updateDTO.getId(), userId);
        
        Announcement announcement = announcementMapper.selectById(updateDTO.getId());
        if (announcement == null) {
            log.warn("公告不存在，ID：{}", updateDTO.getId());
            return false;
        }
        
        BeanUtils.copyProperties(updateDTO, announcement);
        announcementMapper.updateById(announcement);
        
        log.info("公告更新成功，ID：{}", updateDTO.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteAnnouncement(Long id, Long userId) {
        log.info("删除公告，ID：{}，操作人：{}", id, userId);
        
        int result = announcementMapper.deleteById(id);
        boolean success = result > 0;
        
        if (success) {
            log.info("公告删除成功，ID：{}", id);
        } else {
            log.warn("公告删除失败，ID：{}", id);
        }
        
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean publishAnnouncement(Long id, Long userId) {
        log.info("发布公告，ID：{}，操作人：{}", id, userId);
        
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            log.warn("公告不存在，ID：{}", id);
            return false;
        }
        
        announcement.setStatus(1);
        announcement.setPublishTime(LocalDateTime.now());
        announcementMapper.updateById(announcement);
        
        log.info("公告发布成功，ID：{}", id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean offlineAnnouncement(Long id, Long userId) {
        log.info("下线公告，ID：{}，操作人：{}", id, userId);
        
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            log.warn("公告不存在，ID：{}", id);
            return false;
        }
        
        announcement.setStatus(2);
        announcementMapper.updateById(announcement);
        
        log.info("公告下线成功，ID：{}", id);
        return true;
    }

    @Override
    public IPage<AnnouncementVO> getAnnouncementPage(Page<AnnouncementVO> page, Integer type, Integer status, String title) {
        log.info("分页查询公告列表，类型：{}，状态：{}，标题：{}", type, status, title);
        
        return announcementMapper.selectAnnouncementPage(page, type, status, title);
    }

    @Override
    public AnnouncementVO getAnnouncementById(Long id) {
        log.info("查询公告详情，ID：{}", id);
        
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            log.warn("公告不存在，ID：{}", id);
            return null;
        }
        
        AnnouncementVO vo = new AnnouncementVO();
        BeanUtils.copyProperties(announcement, vo);
        
        // 设置类型名称
        vo.setTypeName(getTypeName(announcement.getType()));
        vo.setPriorityName(getPriorityName(announcement.getPriority()));
        vo.setStatusName(getStatusName(announcement.getStatus()));
        
        return vo;
    }

    @Override
    public List<AnnouncementVO> getLatestAnnouncements(Integer limit) {
        log.info("查询最新公告列表，限制数量：{}", limit);
        
        if (limit == null || limit <= 0) {
            limit = 5; // 默认5条
        }
        
        return announcementMapper.selectLatestAnnouncements(limit);
    }

    @Override
    public AnnouncementVO getSystemAnnouncement() {
        log.info("查询系统公告");
        
        return announcementMapper.selectSystemAnnouncement();
    }

    @Override
    public List<AnnouncementVO> getTopAnnouncements() {
        log.info("查询置顶公告");
        
        return announcementMapper.selectTopAnnouncements();
    }

    /**
     * 获取类型名称
     */
    private String getTypeName(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 1: return "系统公告";
            case 2: return "日常公告";
            case 3: return "维护通知";
            case 4: return "活动公告";
            default: return "未知";
        }
    }

    /**
     * 获取优先级名称
     */
    private String getPriorityName(Integer priority) {
        if (priority == null) return "未知";
        switch (priority) {
            case 1: return "普通";
            case 2: return "重要";
            case 3: return "紧急";
            default: return "未知";
        }
    }

    /**
     * 获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "草稿";
            case 1: return "已发布";
            case 2: return "已下线";
            default: return "未知";
        }
    }
}
