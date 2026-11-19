package com.campus.sportsbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SportsBuddyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SportsBuddyApplication.class, args);
    }
}
