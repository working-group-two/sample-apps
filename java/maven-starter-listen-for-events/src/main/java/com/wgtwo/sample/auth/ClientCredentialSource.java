package com.wgtwo.sample.auth;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.util.function.Supplier;

public class ClientCredentialSource {
    private static final Token PLACEHOLDER = new Token("", 0);
    private final Supplier<Token> tokenSupplier;

    public ClientCredentialSource(Supplier<Token> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    private volatile Token cached = PLACEHOLDER;

    public String accessToken() {
        if (cached.isExpired()) {
            cached = tokenSupplier.get();
        }
        return cached.accessToken;
    }

    public static ClientCredentialSource of(OAuth20Service service, String scope) {
        Supplier<Token> tokenSupplier = () -> {
            try {
                OAuth2AccessToken token = service.getAccessTokenClientCredentialsGrant(scope);
                return new Token(token.getAccessToken(), token.getExpiresIn().intValue());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        return new ClientCredentialSource(tokenSupplier);
    }
}
