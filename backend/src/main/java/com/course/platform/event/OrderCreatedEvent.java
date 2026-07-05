package com.course.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 订单创建事件
 * 在订单创建事务提交后发布，触发后续的自动对接处理
 * 
 * @author AI Assistant
 * @since 2025-11-22
 */
@Getter
public class OrderCreatedEvent extends ApplicationEvent {

    /**
     * 订单ID
     */
    private final Long orderId;

    /**
     * 平台ID
     */
    private final Long platformId;

    /**
     * 是否自营订单：0-否 1-是
     */
    private final Integer isSelfOperated;

    /**
     * 构造函数
     *
     * @param source         事件源
     * @param orderId        订单ID
     * @param platformId     平台ID
     * @param isSelfOperated 是否自营订单
     */
    public OrderCreatedEvent(Object source, Long orderId, Long platformId, Integer isSelfOperated) {
        super(source);
        this.orderId = orderId;
        this.platformId = platformId;
        this.isSelfOperated = isSelfOperated;
    }
}
