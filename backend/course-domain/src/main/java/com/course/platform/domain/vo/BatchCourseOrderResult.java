package com.course.platform.domain.vo;
import java.util.List;

/** 新批量接口逐项返回真实结果，旧接口仍只返回成功订单ID。 */
public record BatchCourseOrderResult(List<Long> orderIds, List<Item> items) {
    public record Item(int index, Long orderId, boolean success, Integer errorCode, String message) {}
}
