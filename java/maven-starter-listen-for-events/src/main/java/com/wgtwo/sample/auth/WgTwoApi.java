package com.wgtwo.sample.auth;

import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.Verb;

public class WgTwoApi extends DefaultApi20 {
    protected WgTwoApi() {
    }

    public static WgTwoApi instance() {
        return InstanceHolder.INSTANCE;
    }

    public Verb getAccessTokenVerb() {
        return Verb.POST;
    }

    public String getAccessTokenEndpoint() {
        return "https://id.wgtwo.com/oauth2/token";
    }

    protected String getAuthorizationBaseUrl() {
        return "https://id.wgtwo.com/oauth2/auth";
    }

    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
        return OpenIdJsonTokenExtractor.instance();
    }

    private static class InstanceHolder {
        private static final WgTwoApi INSTANCE = new WgTwoApi();

        private InstanceHolder() {}
    }
}
