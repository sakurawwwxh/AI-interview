package interview.guide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Interview Platform - Main Application
 * 智能AI面试官平台 - 主启动类
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableRetry
public class App {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
        if (context.getEnvironment().matchesProfiles("legacy-migration")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
