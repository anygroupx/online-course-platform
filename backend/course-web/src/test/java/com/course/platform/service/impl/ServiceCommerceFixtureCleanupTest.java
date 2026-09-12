package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceCommerceFixtureCleanupTest {
    @Test
    void repeatedFixturesReleaseTheirInMemoryDatabaseAndWorkers() throws Exception {
        for (int iteration = 0; iteration < 3; iteration++) {
            var fixture = new ServiceCommerceTransactionTest();
            try {
                fixture.setup();
                var dataSource = (DriverManagerDataSource) fixture.jdbc.getDataSource();
                assertNotNull(dataSource);
                String existingOnly = dataSource.getUrl() + ";IFEXISTS=TRUE";
                try (var connection = DriverManager.getConnection(existingOnly, "sa", "")) {
                    assertFalse(connection.isClosed());
                }
                fixture.cleanup();
                assertTrue(fixture.threads.isTerminated());
                assertNull(SecurityContextHolder.getContext().getAuthentication());
                SQLException missing = assertThrows(SQLException.class, () -> {
                    try (var ignored = DriverManager.getConnection(existingOnly, "sa", "")) {
                        fail("The per-test database must not survive cleanup");
                    }
                });
                assertEquals(90146, missing.getErrorCode(), "H2 reports the closed database as absent");
            } finally {
                fixture.cleanup();
            }
        }
    }

    @Test
    void cleanupAlsoHandlesAnUninitializedFixture() {
        assertDoesNotThrow(() -> new ServiceCommerceTransactionTest().cleanup());
    }
}
