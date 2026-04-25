package com.featuretoggle.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Feature Toggle Service Server Application
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@SpringBootApplication
public class FeatureToggleServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeatureToggleServerApplication.class, args);
    }
}
