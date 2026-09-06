package com.course.platform.service.impl;

import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.external.AqksApiClient;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseQueryServiceImplTest {
    private final CoursePlatformMapper platforms = mock(CoursePlatformMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final PlatformDockingService docking = mock(PlatformDockingService.class);
    private final CourseQueryServiceImpl service = new CourseQueryServiceImpl(platforms, users,
            mock(OperationLogService.class), docking, mock(AqksApiClient.class));
    private CoursePlatform platform;
    private QueryCourseRequest request;

    @BeforeEach
    void setUp() {
        when(users.selectById(7L)).thenReturn(new User());
        platform = new CoursePlatform();
        platform.setId(42L);
        platform.setQueryApiId(6L);
        when(platforms.selectById(42L)).thenReturn(platform);
        request = new QueryCourseRequest();
        request.setPlatformId(42L);
        request.setStudentAccount("test-student");
        request.setStudentPassword("test-password");
    }

    @ParameterizedTest
    @EnumSource(ProviderRequestException.Reason.class)
    void providerFailureKeepsOriginalReasonAndErrorIdThroughServiceAndPublicResponse(ProviderRequestException.Reason reason) {
        var original = new ProviderRequestException(reason);
        when(docking.queryCourses(platform, request)).thenThrow(original);
        var failure = assertThrows(ProviderRequestException.class, () -> service.queryCourses(request, 7L));
        assertSame(original, failure);
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/courses/query");
        http.setContextPath("/api");
        var response = new GlobalExceptionHandler(mock(SecurityAuditService.class))
                .handleProviderRequestException(failure, http);
        assertEquals(502, response.getStatusCode().value());
        assertEquals(original.getErrorId(), response.getBody().getErrorId());
        assertEquals(ProviderRequestException.PUBLIC_MESSAGE, response.getBody().getMessage());
        assertNull(response.getBody().getData());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void unexpectedFailureStillDoesNotLeakInternalMessagesOrCredentials() {
        when(docking.queryCourses(platform, request)).thenThrow(new IllegalStateException("test-password secret-api-key"));
        var failure = assertThrows(BusinessException.class, () -> service.queryCourses(request, 7L));
        assertEquals("第三方查课服务暂不可用", failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void successfulResponseRemainsUnchanged() {
        when(docking.queryCourses(platform, request)).thenReturn(List.of());
        var result = service.queryCourses(request, 7L);
        assertEquals("查询成功", result.getMessage());
        assertEquals("test-student", result.getStudentAccount());
        assertTrue(result.getCourses().isEmpty());
    }
}
