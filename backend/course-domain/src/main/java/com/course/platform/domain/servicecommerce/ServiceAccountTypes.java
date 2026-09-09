package com.course.platform.domain.servicecommerce;

import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.Lookup;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.PreparedOrder;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/** Temporary, owner-bound account authorization. No upstream credential is a browser capability. */
public final class ServiceAccountTypes {
    private ServiceAccountTypes() {}

    public record StartForm(
            @NotBlank @Pattern(regexp = "PASSWORD|SMS") String mode,
            @NotBlank @Size(max = 100) String account,
            @Size(max = 120) String schoolName,
            boolean authorizedAccount) {
        @Override
        public String toString() {
            return "StartForm[mode=" + mode + ", account=REDACTED]";
        }
    }

    public record VerifyForm(@NotBlank @Size(max = 200) String secret) {
        @Override
        public String toString() {
            return "VerifyForm[secret=REDACTED]";
        }
    }

    public record SessionView(
            String id,
            Long productId,
            String mode,
            String state,
            String accountLabel,
            LocalDateTime expiresAt,
            Lookup lookup,
            boolean canRefreshRules,
            FaceSessionView face) {
        public SessionView(
                String id,
                Long productId,
                String mode,
                String state,
                String accountLabel,
                LocalDateTime expiresAt,
                Lookup lookup,
                boolean canRefreshRules) {
            this(
                    id,
                    productId,
                    mode,
                    state,
                    accountLabel,
                    expiresAt,
                    lookup,
                    canRefreshRules,
                    null);
        }
    }

    /** Only non-secret collection metadata. Never return a token, image, or collection URL here. */
    public record FaceSessionView(
            String collectionOrigin,
            FaceStatus status,
            boolean canCollect,
            boolean canCheck,
            boolean canLaunch) {}

    public record FaceStatus(
            boolean completed, int fileCount, int minFileCount, int maxFileCount) {}

    public record FaceConsentForm(boolean authorizedFace) {}

    /** Local, single-use handoff, sent in a form POST, never an upstream credential or a query. */
    public record FaceLaunchTicket(String sessionId, String ticket, LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "FaceLaunchTicket[REDACTED]";
        }
    }

    public record HeishaAccount(
            String preflightJson,
            String faceToken,
            String collectUrl,
            FaceStatus status,
            String launchDigest,
            LocalDateTime launchExpiresAt) {
        public HeishaAccount collected(String token, String url, FaceStatus state) {
            return new HeishaAccount(preflightJson, token, url, state, null, null);
        }

        public HeishaAccount checked(FaceStatus state) {
            return collected(faceToken, collectUrl, state);
        }

        public HeishaAccount launch(String digest, LocalDateTime expiresAt) {
            return new HeishaAccount(
                    preflightJson, faceToken, collectUrl, status, digest, expiresAt);
        }

        @Override
        public String toString() {
            return "HeishaAccount[REDACTED]";
        }
    }

    public record AccountSnapshot(
            String account,
            String password,
            String schoolName,
            String studentId,
            Lookup lookup,
            HeishaAccount heisha) {
        public AccountSnapshot(
                String account,
                String password,
                String schoolName,
                String studentId,
                Lookup lookup) {
            this(account, password, schoolName, studentId, lookup, null);
        }

        public AccountSnapshot withHeisha(HeishaAccount value) {
            return new AccountSnapshot(account, password, schoolName, studentId, lookup, value);
        }

        @Override
        public String toString() {
            return "AccountSnapshot[REDACTED]";
        }
    }

    public record AccountPreparation(
            PreparedOrder order, String sessionId, long version, LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "AccountPreparation[sessionId=" + sessionId + ", fields=REDACTED]";
        }
    }
}
