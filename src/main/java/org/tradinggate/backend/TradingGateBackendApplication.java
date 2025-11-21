package org.tradinggate.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TradingGateBackendApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        System.setProperty("spring.profiles.active", dotenv.get("spring.profiles.active"));
        SpringApplication.run(TradingGateBackendApplication.class, args);
    }
}
