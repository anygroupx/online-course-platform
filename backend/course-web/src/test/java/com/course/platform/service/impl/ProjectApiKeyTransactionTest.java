package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;

class ProjectApiKeyTransactionTest extends ProjectClientTestSupport {
    @Test
    void readSettingsNeverIssuesAKeyAndIssuanceIsFreeWithHashedStorageOnly() {
        assertFalse(keys.settings("OWNER").configured());
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM project_api_credential", Integer.class));
        var issued = issue("OWNER", "READ_ONLY");
        assertTrue(issued.secret().matches("npo_[0-9a-f]{64}"));
        var stored = credentials.selectList(null).get(0);
        assertEquals(TokenHashUtil.sha256(issued.secret()), stored.getKeyHash());
        assertFalse(stored.toString().contains(issued.secret()));
        assertFalse(issued.toString().contains(issued.secret()));
        assertFalse(keys.settings("OWNER").toString().contains(issued.secret()));
        assertEquals(0, ledgerCount());
        wallet("100");
    }

    @Test
    void readOnlyMasterCannotCreateFundOrChangeCustomers() {
        var c = open("1");
        var issued = issue("OWNER", "READ_ONLY");
        SecurityContextHolder.clearContext();
        var actor = keys.authenticate(issued.secret());
        assertEquals(c.id(), service.client(actor, c.id()).id());
        assertThrows(BusinessException.class, () -> service.quote(actor, opening("1")));
        assertThrows(
                BusinessException.class,
                () ->
                        service.status(
                                actor, c.id(), new StatusForm(c.version(), "SUSPENDED", true)));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        wallet("99.75");
    }

    @Test
    void manageKeyCanUseQuoteAndConfirmButNeverCreatesWebOrAdminAuthentication() {
        var issued = issue("OWNER", "MANAGE");
        SecurityContextHolder.clearContext();
        var actor = keys.authenticate(issued.secret());
        var op = service.quote(actor, opening("4"));
        service.confirm(actor, op.id(), new ConfirmForm(true));
        wallet("99");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertThrows(
                BusinessException.class,
                () ->
                        keys.issue(
                                "OWNER",
                                new KeyForm(
                                        issued.settings().version(),
                                        PASSWORD,
                                        "MANAGE",
                                        30,
                                        true)));
    }

    @Test
    void customerKeyOnlyReadsThatCustomerAndCannotListOrMoveFunds() {
        var a = open("1");
        var b = open("1");
        var issued = issue(a.id(), "READ_ONLY");
        SecurityContextHolder.clearContext();
        var actor = keys.authenticate(issued.secret());
        assertEquals(a.id(), service.client(actor, a.id()).id());
        assertThrows(BusinessException.class, () -> service.client(actor, b.id()));
        assertThrows(BusinessException.class, () -> service.clients(actor, 1L, 1, 20));
        assertThrows(BusinessException.class, () -> service.projects(actor, 1, 20));
        assertThrows(BusinessException.class, () -> service.stats(actor));
        assertThrows(BusinessException.class, () -> service.quote(actor, opening("1")));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void customerKeysCannotBeIssuedWithManageScope() {
        var c = open("0");
        assertThrows(
                BusinessException.class,
                () -> keys.issue(c.id(), new KeyForm(0L, PASSWORD, "MANAGE", 30, true)));
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM project_api_credential", Integer.class));
    }

    @Test
    void keyRotationInvalidatesOriginalKeyAndAlreadyAuthenticatedCaller() {
        var original = issue("OWNER", "MANAGE");
        var caller = keys.authenticate(original.secret());
        var op = service.quote(caller, opening("4"));
        var replacement = issue("OWNER", "MANAGE");
        assertThrows(BusinessException.class, () -> keys.authenticate(original.secret()));
        assertThrows(
                BusinessException.class,
                () -> service.confirm(caller, op.id(), new ConfirmForm(true)));
        assertNotEquals(original.secret(), replacement.secret());
        wallet("100");
    }

    @Test
    void revokeDoesNotDeleteCustomersOrTheirBalancesAndCannotBeUndoneByOldVersion() {
        var c = open("4");
        var issued = issue("OWNER", "MANAGE");
        keys.revoke("OWNER", new KeyRevoke(issued.settings().version(), PASSWORD, true));
        assertThrows(BusinessException.class, () -> keys.authenticate(issued.secret()));
        assertThrows(
                BusinessException.class,
                () ->
                        keys.issue(
                                "OWNER",
                                new KeyForm(
                                        issued.settings().version(),
                                        PASSWORD,
                                        "MANAGE",
                                        30,
                                        true)));
        assertEquals(
                "1.00",
                new java.math.BigDecimal(service.client(keys.web(), c.id()).refundBudget())
                        .setScale(2)
                        .toPlainString());
        wallet("99");
    }

    @Test
    void expiredKeysDisabledOwnersAndPasswordResetFlagCannotAccessApi() {
        var issued = issue("OWNER", "MANAGE");
        jdbc.update(
                "UPDATE project_api_credential SET expires_at=?",
                ServiceTime.now().minusSeconds(1));
        assertThrows(BusinessException.class, () -> keys.authenticate(issued.secret()));
        var current = issue("OWNER", "MANAGE");
        jdbc.update("UPDATE sys_user SET must_change_password=1 WHERE id=7");
        assertThrows(BusinessException.class, () -> keys.authenticate(current.secret()));
        jdbc.update("UPDATE sys_user SET must_change_password=0,status=0 WHERE id=7");
        assertThrows(BusinessException.class, () -> keys.authenticate(current.secret()));
    }

    @Test
    void suspensionDisablesCustomerKeyWithoutTouchingFunds() {
        var c = open("4");
        var issued = issue(c.id(), "READ_ONLY");
        service.status(keys.web(), c.id(), new StatusForm(c.version(), "SUSPENDED", true));
        assertThrows(BusinessException.class, () -> keys.authenticate(issued.secret()));
        wallet("99");
    }

    @Test
    void ownerCannotRotateOrInspectAnotherOwnersCustomerCredential() {
        var c = open("0");
        auth(8);
        assertThrows(BusinessException.class, () -> keys.settings(c.id()));
        assertThrows(BusinessException.class, () -> issue(c.id(), "READ_ONLY"));
    }

    @Test
    void passwordAndConsentMustBeCheckedForIssuanceOrRevocation() {
        assertThrows(
                BusinessException.class,
                () -> keys.issue("OWNER", new KeyForm(0L, "wrong", "MANAGE", 30, true)));
        assertThrows(
                BusinessException.class,
                () -> keys.issue("OWNER", new KeyForm(0L, PASSWORD, "MANAGE", 30, false)));
        assertThrows(
                BusinessException.class,
                () -> keys.issue("OWNER", new KeyForm(0L, PASSWORD, "MANAGE", 0, true)));
        assertFalse(keys.settings("OWNER").configured());
    }

    @Test
    void simultaneousIssuanceForSameVersionYieldsOnlyOneUsableKey() throws Exception {
        var ready = new CountDownLatch(1);
        Callable<String> issue =
                () -> {
                    auth(7);
                    ready.await();
                    try {
                        return keys.issue("OWNER", new KeyForm(0L, PASSWORD, "MANAGE", 30, true))
                                .secret();
                    } catch (BusinessException e) {
                        return "REJECTED";
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
        var a = threads.submit(issue);
        var b = threads.submit(issue);
        ready.countDown();
        var result = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        assertEquals(1, result.stream().filter("REJECTED"::equals).count());
        assertEquals(
                1,
                jdbc.queryForObject("SELECT COUNT(*) FROM project_api_credential", Integer.class));
    }

    @Test
    void rateKeysAndApiCallHistoryContainNoCredentialOrRequestPayload() {
        var issued = issue("OWNER", "MANAGE");
        var actor = keys.authenticate(issued.secret());
        keys.record(actor, "QUOTE", false);
        keys.record(actor, "CLIENTS", true);
        var history = keys.calls(1, 20);
        assertEquals(2, history.getTotal());
        assertTrue(history.getRecords().stream().anyMatch(x -> x.outcome().equals("FAILED")));
        assertFalse(history.getRecords().toString().contains(issued.secret()));
        verify(limiter, atLeastOnce())
                .check(
                        argThat(
                                r ->
                                        r.dimension().equals("project-key:auth")
                                                && r.keyMaterial()
                                                        .equals(
                                                                TokenHashUtil.sha256(
                                                                        issued.secret()))));
    }

    @Test
    void oldReadOnlyCallerCannotForgeManageOrSwitchSubject() {
        var issued = issue("OWNER", "READ_ONLY");
        var c = keys.authenticate(issued.secret());
        var forged = new Caller(c.ownerId(), c.credentialId(), c.credentialVersion(), null, true);
        assertThrows(BusinessException.class, () -> service.quote(forged, opening("1")));
    }

    @Test
    void disabledFeatureStillAllowsExplicitRevocationButNoNewSecret() {
        var issued = issue("OWNER", "MANAGE");
        ReflectionTestUtils.setField(keys, "enabled", false);
        assertThrows(BusinessException.class, () -> keys.authenticate(issued.secret()));
        assertThrows(BusinessException.class, () -> issue("OWNER", "MANAGE"));
        assertFalse(
                keys.revoke("OWNER", new KeyRevoke(issued.settings().version(), PASSWORD, true))
                        .configured());
    }

    @Test
    void closedCustomerCannotBeGivenANewCredential() {
        var c = open("0");
        service.status(keys.web(), c.id(), new StatusForm(c.version(), "CLOSED", true));
        assertThrows(BusinessException.class, () -> issue(c.id(), "READ_ONLY"));
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM project_api_credential", Integer.class));
    }
}
