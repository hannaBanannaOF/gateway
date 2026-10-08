package com.liminallabs.gateway.auth_provider.application;

/** Falha transitória ou de configuração ao falar com o provider. A sessão não deve ser descartada por isso. */
public class AuthProviderUnavailableException extends RuntimeException {

    public AuthProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
