package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.orderreceipt.OrderReceiptRecovery;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface OrderReceiptRecoveryMapper extends BaseMapper<OrderReceiptRecovery> {
    @Select("SELECT * FROM course_order_receipt_recovery WHERE actor_id=#{actor} AND order_id=#{order} ORDER BY create_time DESC,id DESC LIMIT 20")
    List<OrderReceiptRecovery> recent(@Param("actor") long actor, @Param("order") long order);
    @Select("SELECT * FROM course_order_receipt_recovery WHERE id=#{id} FOR UPDATE")
    OrderReceiptRecovery lock(@Param("id") String id);
    @Select("SELECT * FROM course_order WHERE id=#{id} FOR UPDATE")
    CourseOrder lockOrder(@Param("id") long id);
    @Select("SELECT * FROM course_platform WHERE id=#{id} FOR UPDATE")
    CoursePlatform lockPlatform(@Param("id") long id);
    @Select("SELECT * FROM api_provider WHERE id=#{id} FOR UPDATE")
    ApiProvider lockProvider(@Param("id") long id);
    @Select("SELECT * FROM api_provider WHERE provider_type='27' ORDER BY id LIMIT 501")
    List<ApiProvider> benzProviders();
    @Select("<script>SELECT id FROM course_order WHERE api_provider_id IN <foreach collection='providers' item='p' open='(' separator=',' close=')'>#{p}</foreach> AND third_order_id=#{receipt} LIMIT 2 FOR UPDATE</script>")
    List<Long> boundOrders(@Param("providers") List<Long> providers, @Param("receipt") String receipt);
    @Select("<script>SELECT o.id FROM course_order o JOIN course_platform p ON p.id=o.platform_id WHERE o.api_provider_id IN <foreach collection='providers' item='p' open='(' separator=',' close=')'>#{p}</foreach> AND o.student_account=#{account} AND o.student_password=#{password} AND o.course_name=#{course} AND p.dock_param=#{product} ORDER BY o.id LIMIT 2 FOR UPDATE</script>")
    List<Long> matchingOrders(@Param("providers") List<Long> providers, @Param("account") String account,
                              @Param("password") String password, @Param("course") String course, @Param("product") String product);
    @Select("SELECT COUNT(*) FROM course_order_receipt_claim WHERE (source_identity=#{source} AND receipt_id=#{receipt}) OR order_id=#{order}")
    long claimed(@Param("source") String source, @Param("receipt") String receipt, @Param("order") long order);
    @Insert("INSERT INTO course_order_receipt_claim(source_identity,receipt_id,order_id,recovery_id,create_time) VALUES(#{sourceIdentity},#{receiptId},#{orderId},#{id},#{updateTime})")
    int claim(OrderReceiptRecovery recovery);
    // Receipt association only. Do not overwrite order state, progress, money, credentials or supplier configuration.
    @Update("UPDATE course_order SET third_order_id=#{receipt},update_time=#{at} WHERE id=#{order} AND is_deleted=0 AND (third_order_id IS NULL OR third_order_id='')")
    int bind(@Param("order") long order, @Param("receipt") String receipt, @Param("at") java.time.LocalDateTime at);
}
