package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.order.CourseOrderProgressLogService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.security.ResourceAuthorizationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CourseOrderCourseIdCapacityTest {

    @Test
    void schemaAndMigrationPreserveLongOpaqueCourseIdentifiers() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("database/schema.sql"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "repository root not found");

        String schema = Files.readString(root.resolve("database/schema.sql"));
        assertTrue(schema.matches("(?s).*`course_id`\\s+VARCHAR\\(2048\\).*"));

        String migration = Files.readString(
                        root.resolve("database/migrations/031_expand_course_order_course_id.sql"))
                .lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n")
                .trim();

        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:course-id-capacity;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE course_order (course_id VARCHAR(100))");
                statement.execute(migration);
            }

            String courseId = "opaque-course-id:" + "x".repeat(1008);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO course_order(course_id) VALUES (?)")) {
                insert.setString(1, courseId);
                assertEquals(1, insert.executeUpdate());
            }
            try (var query = connection.createStatement()
                    .executeQuery("SELECT course_id FROM course_order")) {
                assertTrue(query.next());
                assertEquals(courseId, query.getString(1));
            }
        }
    }

    @Test
    void serviceRejectsIdentifiersBeyondTheDatabaseLimitBeforeAnyWrite() {
        CourseOrderMapper orders = mock(CourseOrderMapper.class);
        CoursePlatformMapper platforms = mock(CoursePlatformMapper.class);
        UserMapper users = mock(UserMapper.class);
        CourseOrderServiceImpl service = new CourseOrderServiceImpl(
                orders,
                platforms,
                users,
                mock(OperationLogService.class),
                mock(PlatformDockingService.class),
                mock(ApiProviderMapper.class),
                mock(ApplicationEventPublisher.class),
                mock(AccountLedgerServiceImpl.class),
                new ResourceAuthorizationService(),
                mock(CourseOrderProgressLogService.class));
        OrderCreateRequest request = new OrderCreateRequest();
        request.setCourseId("x".repeat(2049));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.createOrder(request, 7L));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        assertEquals("课程ID长度不能超过2048个字符", error.getMessage());
        verifyNoInteractions(orders, platforms, users);
    }
}
