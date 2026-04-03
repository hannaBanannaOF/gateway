package com.liminallabs.gateway.auth_provider.application;

import com.liminallabs.gateway.token.domain.Token;

public interface AuthProvider {
    String getAuthorizationUrl(String redirectUri, String state);
    Token exchangeCodeForToken(String code, String redirectUri);
    Token refreshToken(String refreshToken);
    boolean validateToken(String token);
    String getProviderName();
}
