package com.course.platform.application.service.projectclient;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;

public interface ProjectApiKeyService {
    Caller web();

    Caller authenticate(String key);

    void recheck(Caller caller, boolean manage, boolean ownerOnly);

    /** SUPPORT customer keys may submit/reply only within that exact customer. */
    void recheckTickets(Caller caller, boolean write);

    KeyView settings(String subject);

    IssuedKey issue(String subject, KeyForm form);

    KeyView revoke(String subject, KeyRevoke form);

    void record(Caller caller, String action, boolean success);

    IPage<ApiCallView> calls(int page, int size);
}
