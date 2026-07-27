package de.venomenon.gridwordsbot;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GridwordsBotProperties.class)
public class GridwordsBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(GridwordsBotApplication.class, args);
    }
}
