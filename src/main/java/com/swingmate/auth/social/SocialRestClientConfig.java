package com.swingmate.auth.social;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SocialRestClientConfig {

    /** 카카오·Google이 응답하지 않을 때 요청 스레드가 무한정 묶이지 않게 타임아웃을 둔다. */
    @Bean
    RestClient socialRestClient(RestClient.Builder builder) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(5));
        return builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }
}
