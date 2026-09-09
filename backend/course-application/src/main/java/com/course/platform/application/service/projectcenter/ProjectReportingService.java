package com.course.platform.application.service.projectcenter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.projectcenter.ProjectReportTypes.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;

public interface ProjectReportingService {
    OwnerReport owner(Caller caller);
    IPage<ProjectBalance> projects(Caller caller, int page, int pageSize);
    SystemReport system();
}
