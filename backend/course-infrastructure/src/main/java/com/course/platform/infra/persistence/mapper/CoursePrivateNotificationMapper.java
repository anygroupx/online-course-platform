package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 周期阶段去重的站内通知出站记录。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateNotificationMapper extends BaseMapper<CoursePrivateNotification> {
    @Select("SELECT * FROM course_private_notification WHERE id=#{id} FOR UPDATE")
    CoursePrivateNotification selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_notification WHERE term_id=#{termId} AND stage=#{stage}")
    CoursePrivateNotification selectByStage(@Param("termId") Long termId, @Param("stage") String stage);
    @Select("SELECT * FROM course_private_notification WHERE status IN ('PENDING','FAILED') AND available_at<=#{now} AND attempts<#{maxAttempts} ORDER BY id LIMIT #{limit}")
    List<CoursePrivateNotification> selectReady(@Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts, @Param("limit") int limit);
    @Update("UPDATE course_private_notification SET status=#{status},delivered_at=#{deliveredAt},available_at=#{availableAt},attempts=attempts+1,version=version+1 WHERE id=#{id} AND version=#{expectedVersion}")
    int updateDelivery(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion, @Param("status") String status, @Param("deliveredAt") LocalDateTime deliveredAt, @Param("availableAt") LocalDateTime availableAt);

    /** 通知轮转只观察当前代次，包含保留有限截止时间的旧兼容周期。 */
    @Select("SELECT t.id FROM course_private_contract_term t JOIN course_private_contract c ON c.current_term_id=t.id AND c.relation_generation=t.relation_generation WHERE t.id>#{afterId} AND c.status NOT IN ('SUSPENDED','TERMINATED') AND t.activated_at IS NOT NULL AND t.status IN ('ACTIVE','EXPIRED','QUALIFIED','PENDING_SETTLEMENT') ORDER BY t.id LIMIT #{limit}")
    List<Long> selectObservationBatch(@Param("afterId") long afterId,@Param("limit") int limit);
    /** 只有发布到站内收件箱的本人通知可见，未读统计与分页使用同一归属条件。 */
    @Select("SELECT * FROM course_private_notification WHERE user_id=#{userId} AND status='DELIVERED' ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<CoursePrivateNotification> selectInbox(@Param("userId") Long userId,@Param("offset") int offset,@Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM course_private_notification WHERE user_id=#{userId} AND status='DELIVERED'")
    long countInbox(@Param("userId") Long userId);
    @Select("SELECT COUNT(*) FROM course_private_notification WHERE user_id=#{userId} AND status='DELIVERED' AND read_at IS NULL")
    long countUnread(@Param("userId") Long userId);
    @Update("UPDATE course_private_notification SET read_at=#{now},version=version+1 WHERE id=#{id} AND user_id=#{userId} AND status='DELIVERED' AND read_at IS NULL")
    int markRead(@Param("id") Long id,@Param("userId") Long userId,@Param("now") LocalDateTime now);
}
