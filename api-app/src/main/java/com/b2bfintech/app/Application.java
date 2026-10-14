package com.b2bfintech.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Single bootable module aggregating every domain module (platform/organisation/client/billing/
 * wallet/notification) - a modular monolith for now, structured so bounded contexts (starting
 * with the async bill-processing worker) can be split into separate deployables later.
 */
@SpringBootApplication(scanBasePackages = "com.b2bfintech")
@EntityScan("com.b2bfintech")
@EnableJpaRepositories("com.b2bfintech")
@EnableScheduling
@EnableAsync
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
