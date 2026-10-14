package com.swingmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// JWT로만 인증하므로 Spring Security 기본 계정(생성된 비밀번호)은 만들지 않는다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SwingMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwingMateApplication.class, args);
    }
}
