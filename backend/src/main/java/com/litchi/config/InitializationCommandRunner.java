package com.litchi.config;

import com.litchi.service.DataInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitializationCommandRunner implements ApplicationRunner {

    private final DataInitializer dataInitializer;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("init")) {
            return;
        }

        String scope = args.getOptionValues("init") == null || args.getOptionValues("init").isEmpty()
                ? "all"
                : args.getOptionValues("init").get(0);

        DataInitializer.InitResult result = dataInitializer.initialize(scope);
        log.info("Explicit initialization completed: {}", result.getMessage());
    }
}
