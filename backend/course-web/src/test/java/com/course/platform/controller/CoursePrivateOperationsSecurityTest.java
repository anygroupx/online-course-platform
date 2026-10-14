package com.course.platform.controller;

import com.course.platform.domain.dto.CoursePrivatePlanRequest;
import com.course.platform.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Arrays;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 实际Spring权限代理验证草稿、发布、成本、风险与资金审核的隔离。 */
class CoursePrivateOperationsSecurityTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean CoursePrivateAnalyticsService analyticsService(){return mock(CoursePrivateAnalyticsService.class);}
        @Bean AdminCoursePrivateAnalyticsController analytics(CoursePrivateAnalyticsService service){return new AdminCoursePrivateAnalyticsController(service);}
        @Bean AdminCoursePrivateSettlementController reviews(CoursePrivateSettlementService service){return new AdminCoursePrivateSettlementController(service);}
        @Bean CoursePrivatePlanService plans(){return mock(CoursePrivatePlanService.class);}
        @Bean CourseWalletReleaseRequestService releases(){return mock(CourseWalletReleaseRequestService.class);}
        @Bean CoursePrivateApplicationService applications(){return mock(CoursePrivateApplicationService.class);}
        @Bean CoursePrivateRenewalService renewal(){return mock(CoursePrivateRenewalService.class);}
        @Bean com.course.platform.service.impl.CoursePrivateUpgradeService upgrade(){return mock(com.course.platform.service.impl.CoursePrivateUpgradeService.class);}
        @Bean com.course.platform.service.impl.CoursePrivateSettlementService settlement(){return mock(com.course.platform.service.impl.CoursePrivateSettlementService.class);}
        @Bean CoursePrivateFulfillmentService fulfillment(){return mock(CoursePrivateFulfillmentService.class);}
        @Bean CoursePrivateRightsReadService rights(){return mock(CoursePrivateRightsReadService.class);}
        @Bean CoursePrivateLifecycleControlService controls(){return mock(CoursePrivateLifecycleControlService.class);}
        @Bean AdminCoursePrivateTermController terms(CoursePrivateRightsReadService reads,CoursePrivateLifecycleControlService controls){return new AdminCoursePrivateTermController(reads,controls);}
        @Bean AdminCoursePrivateFulfillmentController verified(CoursePrivateFulfillmentService service){return new AdminCoursePrivateFulfillmentController(service);}
        @Bean AdminCoursePrivateApplicationController adminApplications(CoursePrivateApplicationService service){return new AdminCoursePrivateApplicationController(service);}
        @Bean AdminCoursePrivatePlanController adminPlan(CoursePrivatePlanService service){return new AdminCoursePrivatePlanController(service);}
        @Bean AdminCourseWalletReleaseRequestController adminRelease(CourseWalletReleaseRequestService service){return new AdminCourseWalletReleaseRequestController(service);}
    }
    AnnotationConfigApplicationContext context;
    AdminCoursePrivatePlanController plans;
    AdminCourseWalletReleaseRequestController releases;
    CoursePrivatePlanService planService;
    CourseWalletReleaseRequestService releaseService;
    @BeforeEach void setup(){context=new AnnotationConfigApplicationContext(Config.class);plans=context.getBean(AdminCoursePrivatePlanController.class);releases=context.getBean(AdminCourseWalletReleaseRequestController.class);planService=context.getBean(CoursePrivatePlanService.class);releaseService=context.getBean(CourseWalletReleaseRequestService.class);/* 容器参考依赖注入不属于受测业务调用，保留权限拒绝后的零业务交互断言。 */clearInvocations(planService);}
    @AfterEach void clear(){context.close();SecurityContextHolder.clearContext();}
    void auth(String... permissions){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));}
    @Test void analyticsNeedsExactCurrentPermission(){var controller=context.getBean(AdminCoursePrivateAnalyticsController.class);auth("ROLE_FINANCE","course-private-term:read");assertThrows(AccessDeniedException.class,()->controller.summary(null,null));auth("course-private-analytics:read");controller.summary(9L,3L);verify(context.getBean(CoursePrivateAnalyticsService.class)).summary(7L,9L,3L);}
    @Test void reviewClosureRequiresAdjustmentAndSettlementPermissions(){var controller=context.getBean(AdminCoursePrivateSettlementController.class);auth("course-private-term:adjust");assertThrows(AccessDeniedException.class,()->controller.resolve(3L,null));auth("course-private-settlement:execute");assertThrows(AccessDeniedException.class,()->controller.resolve(3L,null));auth("course-private-term:adjust","course-private-settlement:execute");controller.resolve(3L,new AdminCoursePrivateSettlementController.ReviewRequest(2,4L,"核验原退款","供应方结算证据","once"));verify(context.getBean(CoursePrivateSettlementService.class)).resolveReview(3L,2,4L,7L,"核验原退款","供应方结算证据","once");}
    @Test void oldRolesAndPlatformPrivilegesCannotControlOperations(){auth("ROLE_OPERATOR","platform:update","user:update","course-private-price:manage");assertThrows(AccessDeniedException.class,()->plans.create(new CoursePrivatePlanRequest()));assertThrows(AccessDeniedException.class,()->releases.approve(3L,new AdminCourseWalletReleaseRequestController.ReviewRequest(0,"批准")));verifyNoInteractions(planService,releaseService);}
    @Test void draftWriterCannotPublishCostVerifyRiskApproveOrRelease(){auth("course-private-plan:manage");plans.create(new CoursePrivatePlanRequest());assertThrows(AccessDeniedException.class,()->plans.publish(3L,new AdminCoursePrivatePlanController.PublishRequest(1,null,null,"发布")));assertThrows(AccessDeniedException.class,()->plans.cost(null));assertThrows(AccessDeniedException.class,()->plans.approveRisk(null));assertThrows(AccessDeniedException.class,()->releases.approve(3L,null));}
    @Test void financeCannotPublishOrApproveRiskByRole(){auth("ROLE_FINANCE","course-wallet:release","course-wallet-release:review","course-private-cost:verify");assertThrows(AccessDeniedException.class,()->plans.publish(3L,null));assertThrows(AccessDeniedException.class,()->plans.approveRisk(null));verifyNoInteractions(planService);}
    @Test void fundsApprovalRequiresBothIndependentPermissions(){auth("course-wallet-release:review");assertThrows(AccessDeniedException.class,()->releases.approve(3L,new AdminCourseWalletReleaseRequestController.ReviewRequest(0,"批准")));auth("course-wallet:release");assertThrows(AccessDeniedException.class,()->releases.approve(3L,new AdminCourseWalletReleaseRequestController.ReviewRequest(0,"批准")));verifyNoInteractions(releaseService);}
    @Test void authorizedFinanceUsesAuthenticatedActorAndRequestVersion(){auth("course-wallet-release:review","course-wallet:release");releases.approve(3L,new AdminCourseWalletReleaseRequestController.ReviewRequest(2,"批准"));verify(releaseService).review(3L,2,7L,true,"批准");}
    @Test void auditorReadsButCannotRejectOrApprove(){auth("course-wallet:audit","course-private-plan:read");when(releaseService.page(null,1,20,null)).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());releases.requests(1,20,null);plans.plans(1,20,null,null);assertThrows(AccessDeniedException.class,()->releases.reject(3L,null));assertThrows(AccessDeniedException.class,()->plans.publish(3L,null));}
    @Test void financeOrDraftWriterCannotApproveEntitlements(){var controller=context.getBean(AdminCoursePrivateApplicationController.class);auth("ROLE_FINANCE","course-wallet:release","course-wallet-release:review");assertThrows(AccessDeniedException.class,()->controller.approve(3L,null));auth("course-private-plan:manage");assertThrows(AccessDeniedException.class,()->controller.applications(1,20,null,null,null));verifyNoInteractions(context.getBean(CoursePrivateApplicationService.class));}
    @Test void entitlementReviewerUsesAuthenticatedActor(){var controller=context.getBean(AdminCoursePrivateApplicationController.class);auth("course-private-contract:review");controller.approve(3L,new AdminCoursePrivateApplicationController.ReviewRequest(2,"审核通过"));verify(context.getBean(CoursePrivateApplicationService.class)).review(3L,2,7L,true,"审核通过");}
    @Test void manualFulfillmentNeedsOrderAndSettlementPermissionsTogether(){var controller=context.getBean(AdminCoursePrivateFulfillmentController.class);auth("order:update");assertThrows(AccessDeniedException.class,()->controller.verify(3L,null));auth("course-private-settlement:execute");assertThrows(AccessDeniedException.class,()->controller.verify(3L,null));verifyNoInteractions(context.getBean(CoursePrivateFulfillmentService.class));}
    @Test void verifiedFulfillmentDerivesActorFromSession(){var controller=context.getBean(AdminCoursePrivateFulfillmentController.class);auth("order:update","course-private-settlement:execute");var date=java.time.LocalDateTime.parse("2026-10-14T10:00:00");controller.verify(3L,new AdminCoursePrivateFulfillmentController.VerificationRequest(date,"核验完成","供应方证明"));verify(context.getBean(CoursePrivateFulfillmentService.class)).verifyExact(3L,date,"核验完成","供应方证明",7L);}
    @Test void manualGrantNeedsReviewAdjustmentAndSettlementPermissionsTogether(){var controller=context.getBean(AdminCoursePrivateApplicationController.class);for(var permissions:java.util.List.of(new String[]{"course-private-contract:review","course-private-term:adjust"},new String[]{"course-private-contract:review","course-private-settlement:execute"},new String[]{"course-private-term:adjust","course-private-settlement:execute"})){auth(permissions);assertThrows(AccessDeniedException.class,()->controller.grantRenewal(3L,new AdminCoursePrivateApplicationController.ReviewRequest(0,"人工补授")));}verifyNoInteractions(context.getBean(CoursePrivateApplicationService.class));}
    @Test void explicitManualGrantDerivesActorFromSession(){auth("course-private-contract:review","course-private-term:adjust","course-private-settlement:execute");var controller=context.getBean(AdminCoursePrivateApplicationController.class);controller.grantRenewal(3L,new AdminCoursePrivateApplicationController.ReviewRequest(2,"按合作证明补授"));verify(context.getBean(CoursePrivateApplicationService.class)).grantRenewal(3L,2,7L,"按合作证明补授");}
    @Test void termReaderCanInspectButCannotAdjustGlobalState(){auth("course-private-term:read");var controller=context.getBean(AdminCoursePrivateTermController.class);controller.term(10L);verify(context.getBean(CoursePrivateRightsReadService.class)).detail(null,10L);assertThrows(AccessDeniedException.class,()->controller.state(4L,null));verifyNoInteractions(context.getBean(CoursePrivateLifecycleControlService.class));}
    @Test void oldPriceManagerCannotAdjustModernLifecycle(){auth("course-private-price:manage","ROLE_OPERATOR");assertThrows(AccessDeniedException.class,()->context.getBean(AdminCoursePrivateTermController.class).state(4L,null));verifyNoInteractions(context.getBean(CoursePrivateLifecycleControlService.class));}
    @Test void authorizedLifecycleControllerUsesCurrentActor(){auth("course-private-term:adjust");context.getBean(AdminCoursePrivateTermController.class).state(4L,new AdminCoursePrivateTermController.StateRequest(2,"SUSPEND","暂停合作","one"));verify(context.getBean(CoursePrivateLifecycleControlService.class)).change(4L,2,7L,"SUSPEND","暂停合作","one");}
}
