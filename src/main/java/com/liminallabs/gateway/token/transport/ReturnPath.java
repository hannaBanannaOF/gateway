package com.liminallabs.gateway.token.transport;

/** Caminho de retorno pós-login. Vem do browser, então só aceitamos caminho relativo ao frontend. */
final class ReturnPath {

    private ReturnPath() {
    }

    /** Evita open redirect como "@evil.com" ou "//evil.com" grudado na frontend-url. */
    static boolean isSafe(String path) {
        return path != null
            && path.startsWith("/")
            && !path.startsWith("//")
            && !path.startsWith("/\\");
    }

    static String orNull(String path) {
        return isSafe(path) ? path : null;
    }
}
