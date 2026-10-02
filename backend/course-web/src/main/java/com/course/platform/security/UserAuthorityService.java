package com.course.platform.security;

import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads roles and permissions from the database for every authenticated
 * request. Permission changes therefore take effect without waiting for JWT
 * expiry and JWT claims cannot grant privilege.
 */
@Service
@RequiredArgsConstructor
public class UserAuthorityService {
    private final UserAuthorityMapper userAuthorityMapper;

    public List<SimpleGrantedAuthority> loadAuthorities(Long userId) {
        List<String> names = userAuthorityMapper.findAuthoritiesByUserId(userId);
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    /** UX projection only. Backend authorization always reloads database authorities. */
    public record AuthoritySnapshot(List<String> roles, List<String> permissions) {}

    public AuthoritySnapshot loadSnapshot(Long userId) {
        List<String> names = loadAuthorities(userId).stream()
                .map(SimpleGrantedAuthority::getAuthority).sorted().toList();
        return new AuthoritySnapshot(
                names.stream().filter(name -> name.startsWith("ROLE_"))
                        .map(name -> name.substring(5)).toList(),
                names.stream().filter(name -> !name.startsWith("ROLE_")).toList());
    }

    public String getPrimaryRole(Long userId) {
        return userAuthorityMapper.findPrimaryRoleByUserId(userId);
    }
}
