package com.course.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.domain.dto.AnnouncementCreateDTO;
import com.course.platform.domain.dto.AnnouncementUpdateDTO;
import com.course.platform.domain.entity.Announcement;
import com.course.platform.domain.vo.AnnouncementVO;

import java.util.List;

/**
 * 公告服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
public interface AnnouncementService {

    /**
     * 创建公告
     * 
     * @param createDTO 创建DTO
     * @param userId 创建人ID
     * @return 公告ID
     */
    Long createAnnouncement(AnnouncementCreateDTO createDTO, Long userId);

    /**
     * 更新公告
     * 
     * @param updateDTO 更新DTO
     * @param userId 操作人ID
     * @return 是否成功
     */
    Boolean updateAnnouncement(AnnouncementUpdateDTO updateDTO, Long userId);

    /**
     * 删除公告
     * 
     * @param id 公告ID
     * @param userId 操作人ID
     * @return 是否成功
     */
    Boolean deleteAnnouncement(Long id, Long userId);

    /**
     * 发布公告
     * 
     * @param id 公告ID
     * @param userId 操作人ID
     * @return 是否成功
     */
    Boolean publishAnnouncement(Long id, Long userId);

    /**
     * 下线公告
     * 
     * @param id 公告ID
     * @param userId 操作人ID
     * @return 是否成功
     */
    Boolean offlineAnnouncement(Long id, Long userId);

    /**
     * 分页查询公告列表
     * 
     * @param page 分页参数
     * @param type 公告类型
     * @param status 状态
     * @param title 标题关键词
     * @return 公告列表
     */
    IPage<AnnouncementVO> getAnnouncementPage(Page<AnnouncementVO> page, Integer type, Integer status, String title);

    /**
     * 根据ID查询公告详情
     * 
     * @param id 公告ID
     * @return 公告详情
     */
    AnnouncementVO getAnnouncementById(Long id);

    /**
     * 查询最新公告列表（用于首页显示）
     * 
     * @param limit 限制数量
     * @return 公告列表
     */
    List<AnnouncementVO> getLatestAnnouncements(Integer limit);

    /**
     * 查询系统公告（用于首次登录弹窗）
     * 
     * @return 系统公告
     */
    AnnouncementVO getSystemAnnouncement();

    /**
     * 查询置顶公告
     * 
     * @return 置顶公告列表
     */
    List<AnnouncementVO> getTopAnnouncements();
}
