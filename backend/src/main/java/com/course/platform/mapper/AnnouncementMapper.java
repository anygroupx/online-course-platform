package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.domain.entity.Announcement;
import com.course.platform.domain.vo.AnnouncementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公告Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {

    /**
     * 分页查询公告列表
     * 
     * @param page 分页参数
     * @param type 公告类型
     * @param status 状态
     * @param title 标题关键词
     * @return 公告列表
     */
    IPage<AnnouncementVO> selectAnnouncementPage(Page<AnnouncementVO> page, 
                                                @Param("type") Integer type,
                                                @Param("status") Integer status,
                                                @Param("title") String title);

    /**
     * 查询最新公告列表（用于首页显示）
     * 
     * @param limit 限制数量
     * @return 公告列表
     */
    List<AnnouncementVO> selectLatestAnnouncements(@Param("limit") Integer limit);

    /**
     * 查询系统公告（用于首次登录弹窗）
     * 
     * @return 系统公告
     */
    AnnouncementVO selectSystemAnnouncement();

    /**
     * 查询置顶公告
     * 
     * @return 置顶公告列表
     */
    List<AnnouncementVO> selectTopAnnouncements();
}
