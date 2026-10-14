package com.swingmate.auth.social;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oauth")
public record OAuthProperties(boolean mockEnabled, Kakao kakao, Google google) {

    public record Kakao(String appId) {
    }

    public record Google(List<String> clientIds) {
    }
}
