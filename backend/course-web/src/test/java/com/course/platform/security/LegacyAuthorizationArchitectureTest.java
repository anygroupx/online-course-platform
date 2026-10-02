package com.course.platform.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keep compatibility serialization/writes, but never authorize from sys_user.role. */
class LegacyAuthorizationArchitectureTest {
    @Test
    void businessAuthorizationCannotUseLegacyUserRoleOrLegacyAdminAuthorities() throws Exception {
        Path root = RbacTestDatabase.repositoryRoot();
        var forbidden = Pattern.compile("\\.getRole\\s*\\(|SecurityRoles\\.(?:ADMIN|CS|USER|ROLE_ADMIN|ROLE_CS)\\b|[\"']ROLE_(?:ADMIN|CS)[\"']");
        List<String> violations = new ArrayList<>();
        for (String module : List.of("course-web", "course-application")) {
            Path source = root.resolve("backend/" + module + "/src/main/java");
            try (var paths = Files.walk(source)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    // Exact compatibility DTO projection, not an authorization decision.
                    boolean legacyDto = path.endsWith("security/SensitiveDataMasker.java");
                    List<String> lines = Files.readAllLines(path);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (legacyDto && line.strip().equals(".role(user.getRole())")) continue;
                        if ((path.endsWith("service/impl/RegisterServiceImpl.java")
                                || path.endsWith("service/impl/UserServiceImpl.java"))
                                && line.strip().matches("user\\.setRole\\((?:com\\.course\\.platform\\.common\\.security\\.)?SecurityRoles\\.USER\\);")) continue;
                        if (forbidden.matcher(line).find()) violations.add(root.relativize(path) + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Legacy authorization is prohibited: " + violations);
    }
    @Test
    void serviceCommerceAndProjectsCannotRegainProviderWriteOrGenericAdminCoupling() throws Exception {
        Path source = RbacTestDatabase.repositoryRoot().resolve("backend/course-web/src/main/java/com/course/platform");
        for (String name : List.of(
                "controller/ServiceCommerceController.java", "service/impl/ServiceCommerceServiceImpl.java",
                "controller/ProjectCenterController.java", "service/impl/ProjectCenterServiceImpl.java",
                "controller/ProjectTicketController.java", "service/impl/ProjectTicketServiceImpl.java",
                "controller/ProjectReportingController.java", "service/impl/ProjectReportingServiceImpl.java",
                "controller/ProjectRecordsController.java", "service/impl/ProjectRecordsServiceImpl.java")) {
            String code = Files.readString(source.resolve(name));
            assertTrue(!code.contains("api-provider:update") && !code.contains("API_PROVIDER_UPDATE")
                    && !code.contains("SecurityUtils.requireAdmin(") && !code.contains("SecurityUtils.isAdmin("),
                    "Domain authorization must not depend on provider administration: " + name);
        }
    }

}
