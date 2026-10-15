package com.course.platform.controller;

import com.course.platform.domain.dto.CoursePrivatePlanRequest;
import com.course.platform.service.impl.CoursePrivatePlanService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP输入不接受资金余额及伪造审核人，只从登录主体取得执行人。 */
class CoursePrivatePlanHttpTest {
    CoursePrivatePlanService service=mock(CoursePrivatePlanService.class);
    MockMvc mvc;
    @BeforeEach void setup(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,List.of()));mvc=MockMvcBuilders.standaloneSetup(new AdminCoursePrivatePlanController(service),new CoursePrivatePlanController(service)).build();}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void publishedRulesUseSessionActorAndExplicitExpectedVersions() throws Exception {
        mvc.perform(post("/admin/course-private-operations/plans/3/publish").contentType(MediaType.APPLICATION_JSON).content("{\"expectedDraftVersion\":2,\"costVersionId\":4,\"riskApprovalId\":6,\"reason\":\"发布\",\"operatorId\":999,\"balance\":999999}" )).andExpect(status().isOk());
        verify(service).publish(3L,2,4L,6L,7L,"发布");
    }
    @Test void selfListCannotReadForgedUsers() throws Exception {
        mvc.perform(get("/course-private-operations/plans").param("userId","999").param("platformId","9")).andExpect(status().isOk());verify(service).visiblePlans(7L,1,20,9L);
    }
    @Test void invalidDraftAndUnsupportedModesFailBeforeService() throws Exception {
        for(String rules:List.of("{\"mode\":\"QUOTA\",\"fixedPrice\":7.20}","{\"mode\":\"PERMANENT_PREPAID\",\"fixedPrice\":7.201}","{\"mode\":\"TIME_PREPAID\",\"fixedPrice\":7.20,\"validityDays\":0}"))mvc.perform(post("/admin/course-private-operations/plans").contentType(MediaType.APPLICATION_JSON).content("{\"platformId\":9,\"planCode\":\"A\",\"planName\":\"长期\",\"reason\":\"配置\",\"rules\":"+rules+"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void nestedRulesAndMetadataAreAcceptedButAdminCannotAssignWallet() throws Exception {
        mvc.perform(post("/admin/course-private-operations/plans").contentType(MediaType.APPLICATION_JSON).content("{\"platformId\":9,\"planCode\":\"A\",\"planName\":\"长期\",\"reason\":\"配置\",\"rules\":{\"mode\":\"PERMANENT_PREPAID\",\"fixedPrice\":7.20},\"walletId\":999,\"userId\":999}")).andExpect(status().isOk());
        var captured=ArgumentCaptor.forClass(CoursePrivatePlanRequest.class);verify(service).save(isNull(),captured.capture(),eq(7L));assertEquals("PERMANENT_PREPAID",captured.getValue().getRules().getMode());
    }
    @Test void approvalRequiresDraftVersionAndReason() throws Exception {
        mvc.perform(post("/admin/course-private-operations/risk-approvals").contentType(MediaType.APPLICATION_JSON).content("{\"planId\":3,\"costVersionId\":4,\"reason\":\"批准\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
}
