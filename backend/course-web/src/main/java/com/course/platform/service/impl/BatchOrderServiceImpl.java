package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.BatchOrderRequest;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.order.BatchOrderService;
import com.course.platform.application.service.order.CourseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量订单服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchOrderServiceImpl implements BatchOrderService {

    private final UserMapper userMapper;
    private final CoursePlatformMapper coursePlatformMapper;
    private final CourseOrderService courseOrderService;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public List<Long> batchCreateOrders(BatchOrderRequest request, Long userId) {
        return batchCreateDetailed(request,userId).orderIds();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public com.course.platform.domain.vo.BatchCourseOrderResult batchCreateDetailed(BatchOrderRequest request, Long userId) {
        if (request == null || request.getPlatformId() == null || request.getOrders() == null
                || request.getOrders().isEmpty() || request.getOrders().size() > 100)
            throw new BusinessException(ResultCode.PARAM_ERROR, "每次批量下单须包含1到100个订单");
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        // 每笔经独立服务代理进入完整事务；禁止用普通余额推算专户购买能力。
        List<Long> ids = new ArrayList<>();
        List<com.course.platform.domain.vo.BatchCourseOrderResult.Item> results = new ArrayList<>();
        for (int index=0;index<request.getOrders().size();index++) {
            BatchOrderRequest.BatchOrderItem item = request.getOrders().get(index);
            try {
                if (item == null) throw new BusinessException(ResultCode.PARAM_ERROR,"订单项不能为空");
                OrderCreateRequest order = new OrderCreateRequest();
                order.setPlatformId(request.getPlatformId());order.setSchoolName(item.getSchoolName());order.setStudentName(item.getStudentName());
                order.setStudentAccount(item.getStudentAccount());order.setStudentPassword(item.getStudentPassword());order.setCourseId(item.getCourseId());order.setCourseName(item.getCourseName());
                order.setClientRequestId(item.getClientRequestId());order.setExpectedAmount(item.getExpectedAmount());order.setExpectedContractVersion(item.getExpectedContractVersion());
                order.setExpectedPriceSource(item.getExpectedPriceSource());order.setExpectedPaymentSource(item.getExpectedPaymentSource());
                Long id = courseOrderService.createOrder(order,userId);ids.add(id);
                results.add(new com.course.platform.domain.vo.BatchCourseOrderResult.Item(index,id,true,null,"创建成功"));
            } catch (BusinessException e) {
                results.add(new com.course.platform.domain.vo.BatchCourseOrderResult.Item(index,null,false,e.getCode(),e.getMessage()));
            } catch (Exception e) {
                log.error("批量订单创建失败：userId={}, item={}",userId,index);
                results.add(new com.course.platform.domain.vo.BatchCourseOrderResult.Item(index,null,false,ResultCode.ERROR.getCode(),"订单创建失败，请检查订单记录后重试"));
            }
        }
        return new com.course.platform.domain.vo.BatchCourseOrderResult(ids,results);
    }
}
