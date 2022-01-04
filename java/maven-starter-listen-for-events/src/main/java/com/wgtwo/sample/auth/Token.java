package com.wgtwo.sample.auth;

import java.time.Clock;
import java.time.Instant;

public class Token {
    private final Clock clock = Clock.systemUTC();
    final String accessToken;
    final Instant expiry;

    /**
     * The token has expiry set two minutes before actual to avoid timing issues
     */
    public Token(String accessToken, int expiresInSeconds) {
        this.accessToken = accessToken;
        this.expiry = clock.instant().plusSeconds(expiresInSeconds).minusSeconds(120);
    }

    public boolean isExpired() {
        return clock.instant().isAfter(expiry);
    }
}