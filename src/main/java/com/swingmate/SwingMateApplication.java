package com.swingmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SwingMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwingMateApplication.class, args);
    }
}
