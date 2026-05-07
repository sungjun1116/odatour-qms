package com.odatour.waiting.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WaitingConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
