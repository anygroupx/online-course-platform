package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateContractTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 同一原专户关系内独立的权益周期与资金资格窗口。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateContractTermMapper extends BaseMapper<CoursePrivateContractTerm> {
    /** 协议锁后读取最新周期序号，加入RR外层事务仍使用当前读。 */
    @Select("SELECT term_no FROM course_private_contract_term WHERE contract_id=#{contractId} ORDER BY term_no DESC LIMIT 1 FOR UPDATE")
    Integer latestTermNoForUpdate(@Param("contractId") Long contractId);
    @Select("SELECT COALESCE(MAX(term_no),0) FROM course_private_contract_term WHERE contract_id=#{contractId}")
    int maximumTermNo(@Param("contractId") Long contractId);
    @Select("SELECT * FROM course_private_contract_term WHERE id=#{id} FOR UPDATE")
    CoursePrivateContractTerm selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_contract_term WHERE contract_id=#{contractId} ORDER BY term_no,id")
    List<CoursePrivateContractTerm> selectByContract(@Param("contractId") Long contractId);
    @Select("SELECT * FROM course_private_contract_term WHERE contract_id=#{contractId} ORDER BY term_no DESC,id DESC LIMIT #{limit}")
    List<CoursePrivateContractTerm> selectRecentByContract(@Param("contractId") Long contractId,@Param("limit") int limit);
    /** 周期列表包含历史与下一期，不能用协议当前指针替代完整周期分页。 */
    @Select("SELECT * FROM course_private_contract_term WHERE (#{userId} IS NULL OR user_id=#{userId}) AND (#{platformId} IS NULL OR platform_id=#{platformId}) AND (#{status} IS NULL OR status=#{status}) ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<CoursePrivateContractTerm> selectOperationsPage(@Param("userId") Long userId,@Param("platformId") Long platformId,@Param("status") String status,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM course_private_contract_term WHERE (#{userId} IS NULL OR user_id=#{userId}) AND (#{platformId} IS NULL OR platform_id=#{platformId}) AND (#{status} IS NULL OR status=#{status})")
    Long countOperations(@Param("userId") Long userId,@Param("platformId") Long platformId,@Param("status") String status);
    @Select("SELECT * FROM course_private_contract_term WHERE renewal_source_term_id=#{sourceTermId}")
    CoursePrivateContractTerm selectByRenewalSource(@Param("sourceTermId") Long sourceTermId);
    /** 仅当前半开窗口内ACTIVE可购买，观察期不会延长旧密价。 */
    @Select("SELECT t.* FROM course_private_contract_term t JOIN course_private_contract c ON c.id=t.contract_id AND c.relation_generation=t.relation_generation WHERE t.user_id=#{userId} AND t.platform_id=#{platformId} AND t.operations_mode<>'LEGACY_PERMANENT' AND t.status IN ('ACTIVE','SCHEDULED') AND (t.start_at IS NULL OR t.start_at<=#{now}) AND (t.end_at IS NULL OR t.end_at>#{now}) ORDER BY t.term_no DESC LIMIT 2")
    List<CoursePrivateContractTerm> selectEffectiveByBinding(@Param("userId") Long userId, @Param("platformId") Long platformId, @Param("now") LocalDateTime now);
    /** 调用者已锁协议；NULL边界表示无限界，不能凭此方法代替锁。 */
    @Select("SELECT COUNT(*) FROM course_private_contract_term t JOIN course_private_contract c ON c.id=t.contract_id AND c.relation_generation=t.relation_generation WHERE t.contract_id=#{contractId} AND (#{excludeId} IS NULL OR t.id<>#{excludeId}) AND t.status IN ('SCHEDULED','ACTIVE') AND (#{endAt} IS NULL OR t.start_at IS NULL OR t.start_at<#{endAt}) AND (t.end_at IS NULL OR #{startAt} IS NULL OR t.end_at>#{startAt})")
    int countOverlapping(@Param("contractId") Long contractId, @Param("excludeId") Long excludeId, @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);
    @Select("SELECT * FROM course_private_contract_term WHERE (status='ACTIVE' AND end_at<=#{now}) OR (status='PENDING_SETTLEMENT' AND settlement_deadline_at<=#{now}) ORDER BY id LIMIT #{limit}")
    List<CoursePrivateContractTerm> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
    /** 有界轮转扫描兜底事实事件，不能依赖上游更新时间冒充业务时间或漏掉历史退款。 */
    @Select("SELECT * FROM course_private_contract_term WHERE id>#{afterId} AND operations_mode<>'LEGACY_PERMANENT' AND status<>'CANCELLED' ORDER BY id LIMIT #{limit}")
    List<CoursePrivateContractTerm> selectReconciliationBatch(@Param("afterId") Long afterId,@Param("limit") int limit);
    /** 周期状态及统计显式版本更新；价格和原始规则快照不会被该方法覆盖。 */
    @Update("UPDATE course_private_contract_term SET status=#{term.status},qualification_status=#{term.qualificationStatus},qualified_deposit_amount=#{term.qualifiedDepositAmount},renewal_net_deposit_amount=#{term.renewalNetDepositAmount},settled_consumption_amount=#{term.settledConsumptionAmount},settled_order_count=#{term.settledOrderCount},adjustment_amount=#{term.adjustmentAmount},qualified_at=#{term.qualifiedAt},finalized_at=#{term.finalizedAt},review_required=#{term.reviewRequired},superseded_at=#{term.supersededAt},version=version+1,updated_at=#{now} WHERE id=#{term.id} AND version=#{expectedVersion}")
    int updateEvaluation(@Param("term") CoursePrivateContractTerm term, @Param("expectedVersion") Integer expectedVersion, @Param("now") LocalDateTime now);

    /** 首次资格及延期排程仅在未生效状态可变，当前成交期的价格和起止不能被重置。 */
    @Update("""
        UPDATE course_private_contract_term SET status=#{term.status},qualification_status=#{term.qualificationStatus},
          start_at=#{term.startAt},end_at=#{term.endAt},settlement_deadline_at=#{term.settlementDeadlineAt},
          activated_at=#{term.activatedAt},qualified_at=#{term.qualifiedAt},qualified_deposit_amount=#{term.qualifiedDepositAmount},qualification_basis_amount=#{term.qualificationBasisAmount},
          funding_end_ledger_id=#{term.fundingEndLedgerId},version=version+1,updated_at=#{now}
        WHERE id=#{term.id} AND version=#{expectedVersion} AND status IN ('PENDING_ACTIVATION','SCHEDULED')
        """)
    int updateActivation(@Param("term") CoursePrivateContractTerm term,@Param("expectedVersion") Integer expectedVersion,@Param("now") LocalDateTime now);
    /** 人工补授只改变未生效排程的资格来源，不重写已冻结的价格、期限和原资金/消费统计。 */
    @Update("UPDATE course_private_contract_term SET qualification_kind='MANUAL_GRANT',qualification_status='QUALIFIED',qualified_at=#{now},review_required=0,version=version+1,updated_at=#{now} WHERE id=#{id} AND renewal_source_term_id=#{sourceId} AND version=#{expectedVersion} AND status='SCHEDULED' AND qualification_kind IN ('PERFORMANCE','NEW_DEPOSIT')")
    int grantScheduled(@Param("id") Long id,@Param("sourceId") Long sourceId,@Param("expectedVersion") Integer expectedVersion,@Param("now") LocalDateTime now);
}
