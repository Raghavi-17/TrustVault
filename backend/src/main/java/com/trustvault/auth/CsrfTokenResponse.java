package com.trustvault.auth;

public record CsrfTokenResponse(
    String token,
    String headerName,
    String parameterName
) {}
