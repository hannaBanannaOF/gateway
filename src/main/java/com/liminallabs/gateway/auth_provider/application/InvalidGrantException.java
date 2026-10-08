package com.liminallabs.gateway.auth_provider.application;

/** O provider rejeitou o code ou o refresh token (OAuth {@code invalid_grant}). Não adianta tentar de novo. */
public class InvalidGrantException extends RuntimeException {

    public InvalidGrantException(String message) {
        super(message);
    }
}
