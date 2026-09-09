package com.course.platform.domain.servicenotification;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public final class ServiceNotificationTypes {
    private ServiceNotificationTypes() {}

    public record ConfigureForm(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,128}") String token,
            @NotNull @Min(0) Long version,
            boolean consent) {
        @Override
        public String toString() {
            return "NotificationConfigure[REDACTED]";
        }
    }

    public record VersionForm(@NotNull @Min(0) Long version, boolean consent) {}

    public record VerifyForm(
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
            @NotNull @Min(0) Long version,
            boolean consent) {
        @Override
        public String toString() {
            return "NotificationVerify[REDACTED]";
        }
    }

    public record SettingsView(
            String orderId,
            boolean configured,
            boolean enabled,
            boolean verified,
            boolean deliveryAvailable,
            boolean canVerify,
            long version,
            String challengeDeliveryId,
            LocalDateTime challengeExpiresAt,
            LocalDateTime verifiedAt,
            List<String> notices) {}

    public record DeliveryView(
            String id,
            String orderId,
            String kind,
            String state,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String notice) {}

    public record Message(String title, String content) {
        @Override
        public String toString() {
            return "NotificationMessage[REDACTED]";
        }
    }

    public record Observation(String status, Integer completed, int quantity, boolean uncertain) {}

    public enum Receipt {
        ACCEPTED,
        REJECTED,
        UNKNOWN
    }
}
