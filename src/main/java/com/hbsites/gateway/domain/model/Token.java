package com.hbsites.gateway.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Token {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("refresh_token")
    private String refreshToken;
    @JsonProperty("expires_in")
    private int expiresIn;
    @JsonProperty("refresh_expires_in")
    private int refreshExpiresIn;

    private LocalDateTime creationDateTime = LocalDateTime.now();

    public boolean isAccessTokenValid() {
        return creationDateTime.plusSeconds(expiresIn).isAfter(LocalDateTime.now());
    }

    public boolean isRefreshTokenValid() {
        return creationDateTime.plusSeconds(refreshExpiresIn).isAfter(LocalDateTime.now());
    }
}