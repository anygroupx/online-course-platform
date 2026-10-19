package com.course.platform.infra.http;

import lombok.Data;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * One trusted DoH endpoint. Bootstrap addresses are literals so resolving the resolver
 * itself never depends on the system resolver this class exists to bypass.
 */
@Data
public class DohEndpointProperties {
    private String host;
    private String path = "dns-query";
    private List<String> bootstrap = new ArrayList<>();

    List<InetAddress> bootstrapAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        for (String literal : bootstrap) {
            if (literal == null || literal.isBlank()) continue;
            InetAddress parsed = parseLiteral(literal.trim());
            if (parsed == null) {
                throw new IllegalStateException("DoH bootstrap entry must be an IP literal");
            }
            addresses.add(parsed);
        }
        return List.copyOf(addresses);
    }

    private static InetAddress parseLiteral(String literal) {
        // Accept only IPv4/IPv6 literals; a hostname here would reintroduce the resolver loop.
        if (!literal.matches("[0-9a-fA-F:.]+")) return null;
        try {
            InetAddress address = InetAddress.getByName(literal);
            return literal.contains(":") == (address.getAddress().length == 16) ? address : null;
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
