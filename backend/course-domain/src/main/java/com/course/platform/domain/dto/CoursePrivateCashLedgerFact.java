package com.course.platform.domain.dto;

import com.course.platform.domain.entity.CourseReservedWalletLedger;
import lombok.Getter;
import lombok.Setter;

/** 账龄与对账只读事实，真实划转及原订单证据通过同一SQL核验，不增加资金字段。 */
@Getter
@Setter
public class CoursePrivateCashLedgerFact extends CourseReservedWalletLedger {
    private Boolean verifiedSource;
}
