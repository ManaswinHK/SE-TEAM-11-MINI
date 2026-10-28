package com.aas.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Tokens revoked by logout or refresh, kept until they would have expired anyway (AAS-SR-008). */
@Component
public class TokenDenylist {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        revoked.put(tokenId, expiresAt);
        Instant now = Instant.now();
        revoked.values().removeIf(exp -> exp.isBefore(now));
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && revoked.containsKey(tokenId);
    }
}
