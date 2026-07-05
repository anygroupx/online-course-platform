package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 运动平台订单实体类
 *
 * @author AI Assistant
 * @since 2025-01-24
 */
@Data
@TableName("sport_run_order")
public class SportRunOrder {
    
    /**
     * 订单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 运动平台类型(ydsj, sdxy, keep, lp, lp2, tsn, xbd, yoma, yyd)
     */
    @TableField("platform_type")
    private String platformType;
    
    /**
     * 平台账号UID
     */
    @TableField("uid")
    private String uid;
    
    /**
     * 剩余次数
     */
    @TableField("residue_num")
    private Integer residueNum;
    
    /**
     * 跑步区域
     */
    @TableField("zone_name")
    private String zoneName;
    
    /**
     * 跑步距离(KM)
     */
    @TableField("run_meter")
    private Double runMeter;
    
    /**
     * 授权标志(0-未授权, 1-已授权)
     */
    @TableField("account_flag")
    private Integer accountFlag;
    
    /**
     * 订单状态(0-未完成, 1-已完成, 2-已暂停, 3-今日异常)
     */
    @TableField("status")
    private Integer status;
    
    /**
     * 跑步状态(0-停止, 1-运行)
     */
    @TableField("run_status")
    private String runStatus;
    
    /**
     * 订单备注
     */
    @TableField("mark_text")
    private String markText;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
