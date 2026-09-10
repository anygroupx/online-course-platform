package com.course.platform.application.service.projectcenter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.projectcenter.ProjectRecordTypes.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;

public interface ProjectRecordsService {
    IPage<Owner> owners(String keyword, int page, int size);
    OwnerDetail owner(long id);
    IPage<Account> accounts(long ownerId, int page, int size);
    IPage<Customer> customers(long ownerId, Long projectId, String status, int page, int size);
    LedgerPage adminLedger(LedgerFilter filter, int page, int size);
    LedgerPage ownLedger(Caller caller, LedgerFilter filter, int page, int size);
}
