package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.security.RbacAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/rbac")
@PreAuthorize("hasAuthority('rbac:manage')")
@RequiredArgsConstructor
public class RbacAdminController {
    private final RbacAdministrationService rbacService;

    public record RoleAssignmentRequest(
            @NotEmpty @Size(max = 6) List<String> roles) {}

    @GetMapping("/roles")
    public Result<List<String>> roles() {
        return Result.success(rbacService.listEnabledRoles());
    }

    @GetMapping("/users/{uid}/roles")
    public Result<List<String>> userRoles(@PathVariable String uid) {
        return Result.success(rbacService.getUserRoles(uid));
    }

    @PutMapping("/users/{uid}/roles")
    public Result<List<String>> replaceRoles(@PathVariable String uid,
                                              @Valid @RequestBody RoleAssignmentRequest request) {
        return Result.success("角色已更新", rbacService.replaceUserRoles(uid, request.roles()));
    }
}
