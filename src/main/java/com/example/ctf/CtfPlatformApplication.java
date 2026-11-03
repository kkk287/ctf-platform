package com.example.ctf;

import com.example.ctf.range.RangeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RangeProperties.class)
public class CtfPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CtfPlatformApplication.class, args);
    }
}
