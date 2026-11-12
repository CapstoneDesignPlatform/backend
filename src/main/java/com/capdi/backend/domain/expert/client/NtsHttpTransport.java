package com.capdi.backend.domain.expert.client;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public interface NtsHttpTransport {
    Response post(URI uri, String json, Duration timeout) throws IOException, InterruptedException;

    record Response(int status, String body) {
        @Override public String toString() { return "NtsHttpResponse[redacted]"; }
    }
}
