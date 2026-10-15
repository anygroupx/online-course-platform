package com.course.platform.controller;

import com.course.platform.domain.dto.CoursePrivateApplicationRequest;
import com.course.platform.service.impl.CoursePrivateApplicationService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP白名单不接受拥有者、成交价或审批状态，页查询和取消始终使用当前账号。 */
class CoursePrivateApplicationHttpTest {
    @Test void explicitGrantRequiresReasonAndVersionAndIgnoresForgedActor() throws Exception {mvc.perform(post("/admin/course-private-operations/applications/3/grant-renewal").contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"reviewReason\":\"合作证明补授\",\"operatorId\":999}")).andExpect(status().isOk());verify(service).grantRenewal(3L,2,7L,"合作证明补授");reset(service);mvc.perform(post("/admin/course-private-operations/applications/3/grant-renewal").contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
    CoursePrivateApplicationService service=mock(CoursePrivateApplicationService.class);
    MockMvc mvc;
    @BeforeEach void setup(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,List.of()));mvc=MockMvcBuilders.standaloneSetup(new CoursePrivateApplicationController(service),new AdminCoursePrivateApplicationController(service)).build();}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void forgedOwnerPriceAndApprovalStatusDoNotEnterSubmission() throws Exception {mvc.perform(post("/course-private-operations/applications").contentType(MediaType.APPLICATION_JSON).content("{\"planId\":1,\"planVersionId\":2,\"reason\":\"申请公开套餐\",\"requestKey\":\"one\",\"userId\":999,\"fixedPrice\":0.01,\"status\":\"APPROVED\"}")).andExpect(status().isOk());var request=ArgumentCaptor.forClass(CoursePrivateApplicationRequest.class);verify(service).submit(eq(7L),request.capture());assertEquals(2L,request.getValue().getPlanVersionId());assertEquals("OPEN",request.getValue().getApplicationType());}
    @Test void ownQueryIgnoresForgedOwnerFilters() throws Exception {mvc.perform(get("/course-private-operations/applications").param("userId","999").param("userUid","forged")).andExpect(status().isOk());verify(service).page(7L,1,20,null,null,null);}
    @Test void cancellationDerivesOwnerFromSession() throws Exception {mvc.perform(post("/course-private-operations/applications/3/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999}")).andExpect(status().isOk());verify(service).cancel(7L,3L);}
    @Test void unsupportedTypesAndMalformedKeysFailBeforeService() throws Exception {for(String type:List.of("QUOTA","PAID_MEMBERSHIP","APPROVED"))mvc.perform(post("/course-private-operations/applications").contentType(MediaType.APPLICATION_JSON).content("{\"planId\":1,\"planVersionId\":2,\"applicationType\":\""+type+"\",\"reason\":\"申请\",\"requestKey\":\"one\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
    @Test void reviewRequiresVersionAndUsesAuthenticatedReviewer() throws Exception {mvc.perform(post("/admin/course-private-operations/applications/3/approve").contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"reviewReason\":\"审核通过\",\"operatorId\":999}")).andExpect(status().isOk());verify(service).review(3L,2,7L,true,"审核通过");reset(service);mvc.perform(post("/admin/course-private-operations/applications/3/approve").contentType(MediaType.APPLICATION_JSON).content("{\"reviewReason\":\"审核通过\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
}
