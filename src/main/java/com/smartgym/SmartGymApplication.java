package com.smartgym;

import com.smartgym.application.GymExtensions;
import com.smartgym.service.SmartGymService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SmartGymApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartGymApplication.class, args);
    }

    @Bean
    public SmartGymService smartGymService() {
        return new SmartGymService();
    }

    @Bean
    public GymExtensions gymExtensions(SmartGymService core) {
        return new GymExtensions(core);
    }
}