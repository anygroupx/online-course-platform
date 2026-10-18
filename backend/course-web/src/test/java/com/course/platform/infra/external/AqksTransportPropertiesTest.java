package com.course.platform.infra.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transport configuration for the AQKS integration.
 *
 * <p>These cases pin the two deployment shapes: an in-campus deployment talks to the origin
 * directly, while an off-campus one tunnels through the WebVPN proxy and carries the
 * transport token. The token is a transport credential and must never be conflated with the
 * per-student application session.</p>
 */
class AqksTransportPropertiesTest {

    @Test
    void defaultsToDirectCampusOriginWithoutTransportToken() {
        var properties = new AqksTransportProperties();
        properties.validate();

        assertEquals("https://aqks.csuft.edu.cn", properties.getBaseUrl());
        assertFalse(properties.usesWebVpn(), "an unconfigured deployment must not claim WebVPN transport");
    }

    @Test
    void normalisesTrailingSlashesOnBaseUrl() {
        var properties = new AqksTransportProperties();
        properties.setBaseUrl("https://https-aqks-csuft-edu-cn-443.webvpn.csuft.edu.cn///");
        properties.validate();

        assertEquals("https://https-aqks-csuft-edu-cn-443.webvpn.csuft.edu.cn", properties.getBaseUrl());
    }

    @Test
    void detectsWebVpnTransportOnlyWhenTokenIsPresent() {
        var properties = new AqksTransportProperties();
        properties.setWebVpnToken("   ");
        properties.validate();
        assertFalse(properties.usesWebVpn(), "a blank token must not enable WebVPN transport");

        properties.setWebVpnToken("  header.payload.signature  ");
        properties.validate();
        assertTrue(properties.usesWebVpn());
        assertEquals("header.payload.signature", properties.getWebVpnToken(), "the token must be trimmed");
    }

    @Test
    void rejectsBlankOrInsecureBaseUrl() {
        var blank = new AqksTransportProperties();
        blank.setBaseUrl("   ");
        assertThrows(IllegalStateException.class, blank::validate);

        var insecure = new AqksTransportProperties();
        insecure.setBaseUrl("http://aqks.csuft.edu.cn");
        assertThrows(IllegalStateException.class, insecure::validate,
                "the origin must be reached over HTTPS");
    }

    @Test
    void fallsBackToDefaultRefererPathWhenUnset() {
        var properties = new AqksTransportProperties();
        properties.setRefererPath("  ");
        properties.validate();

        assertEquals("/client_pc/exone.html", properties.getRefererPath());
    }
}
