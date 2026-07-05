package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付配置实体类
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Data
@TableName("payment_config")
public class PaymentConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 配置ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 环境类型：SANDBOX-沙箱 PRODUCTION-生产
     */
    private String envType;

    /**
     * 支付宝应用APPID
     */
    private String appId;

    /**
     * 应用私钥
     */
    private String privateKey;

    /**
     * 支付宝公钥
     */
    private String alipayPublicKey;

    /**
     * 签名类型：RSA/RSA2
     */
    private String signType;

    /**
     * 数据格式
     */
    private String format;

    /**
     * 字符编码
     */
    private String charset;

    /**
     * 支付宝网关地址
     */
    private String gatewayUrl;

    /**
     * 异步通知地址(可选,支持订单级别覆盖)
     */
    private String notifyUrl;

    /**
     * 同步回调地址(可选,支持订单级别覆盖)
     */
    private String returnUrl;

    /**
     * 是否激活：0-未激活 1-已激活
     */
    private Integer isActive;

    /**
     * 状态：0-禁用 1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
