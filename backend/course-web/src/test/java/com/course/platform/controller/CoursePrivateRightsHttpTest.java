package com.course.platform.controller;

import com.course.platform.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP查询固定本人会话，状态写入只接受白名单及版本，不能伪造拥有者和审批主体。 */
class CoursePrivateRightsHttpTest {
    CoursePrivateRightsReadService reads=mock(CoursePrivateRightsReadService.class);
    CoursePrivateLifecycleControlService controls=mock(CoursePrivateLifecycleControlService.class);
    CoursePrivateNotificationService notifications=mock(CoursePrivateNotificationService.class);
    MockMvc mvc;
    @BeforeEach void setup(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,List.of()));mvc=MockMvcBuilders.standaloneSetup(new CoursePrivateNotificationController(notifications),new CoursePrivateRightsController(reads),new AdminCoursePrivateTermController(reads,controls)).build();}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void noticesIgnoreForgedOwner() throws Exception {mvc.perform(get("/course-private-operations/notifications").param("userId","999")).andExpect(status().isOk());verify(notifications).inbox(7L,1,20);mvc.perform(post("/course-private-operations/notifications/70/read").param("userId","999")).andExpect(status().isOk());verify(notifications).markRead(7L,70L);}
    @Test void selfQueryIgnoresForgedOwnerAndUid() throws Exception {mvc.perform(get("/course-private-operations/rights").param("userId","999").param("userUid","foreign").param("platformId","9")).andExpect(status().isOk());verify(reads).contracts(7L,1,20,9L,null,null);}
    @Test void termOwnershipIsPassedFromSession() throws Exception {mvc.perform(get("/course-private-operations/terms/10").param("userId","999")).andExpect(status().isOk());verify(reads).detail(7L,10L);}
    @Test void administrativeFilteringPassesPublicUuid() throws Exception {mvc.perform(get("/admin/course-private-operations/contracts").param("userUid","00000000-0000-4000-8000-000000000008")).andExpect(status().isOk());verify(reads).contracts(null,1,20,null,null,"00000000-0000-4000-8000-000000000008");}
    @Test void stateAdjustmentIgnoresForgedActorAndMoney() throws Exception {mvc.perform(post("/admin/course-private-operations/contracts/4/state").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2,\"action\":\"SUSPEND\",\"reason\":\"暂停合作\",\"requestKey\":\"one\",\"operatorId\":999,\"userId\":999,\"fixedPrice\":0.01}")).andExpect(status().isOk());verify(controls).change(4L,2,7L,"SUSPEND","暂停合作","one");}
    @Test void missingVersionReasonAndUnsupportedStateFailBeforeService() throws Exception {for(String body:List.of("{\"action\":\"SUSPEND\",\"reason\":\"暂停\",\"requestKey\":\"one\"}","{\"expectedVersion\":1,\"action\":\"ACTIVE\",\"reason\":\"绕过恢复\",\"requestKey\":\"one\"}","{\"expectedVersion\":1,\"action\":\"TERMINATE\",\"requestKey\":\"one\"}"))mvc.perform(post("/admin/course-private-operations/contracts/4/state").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());verifyNoInteractions(controls);}
}
