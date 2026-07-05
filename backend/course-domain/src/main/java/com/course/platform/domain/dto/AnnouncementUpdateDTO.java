package com.course.platform.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 公告更新DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnouncementUpdateDTO {

    /**
     * 公告ID
     */
    private Long id;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容
     */
    private String content;

    /**
     * 公告类型：1-系统公告 2-日常公告 3-维护通知 4-活动公告
     */
    private Integer type;

    /**
     * 优先级：1-普通 2-重要 3-紧急
     */
    private Integer priority = 1;

    /**
     * 是否置顶：0-否 1-是
     */
    private Integer isTop = 0;

    /**
     * 是否弹窗显示：0-否 1-是
     */
    private Integer isPopup = 0;

    /**
     * 状态：0-草稿 1-已发布 2-已下线
     */
    private Integer status = 1;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
}
