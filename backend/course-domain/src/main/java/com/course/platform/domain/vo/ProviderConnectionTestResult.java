package com.course.platform.domain.vo;

import java.time.LocalDateTime;

/** Admin-only, credential-free result of a real, read-only provider operation. */
public record ProviderConnectionTestResult(String apiUrl, String normalizedHost, long durationMs,
                                           LocalDateTime verifiedAt, Long verifiedBy, Integer status) {}
