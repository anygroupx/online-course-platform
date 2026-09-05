package com.course.platform.infra.http;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/** A URI plus the exact DNS answers that a client must use for the connection. */
public record ValidatedDestination(URI uri, String asciiHost, List<InetAddress> pinnedAddresses) {
    public ValidatedDestination {
        pinnedAddresses = List.copyOf(pinnedAddresses);
    }
}
