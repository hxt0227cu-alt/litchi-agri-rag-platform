package com.litchi.config;

import com.litchi.service.DataInitializer;
import com.litchi.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class PlatformStartupBootstrapRunner implements ApplicationRunner {

    private final DataInitializer dataInitializer;
    private final DocumentService documentService;

    @Value("${app.startup.auto-bootstrap:false}")
    private boolean autoBootstrap;

    @Value("${app.startup.max-attempts:8}")
    private int maxAttempts;

    @Value("${app.startup.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoBootstrap) {
            log.info("Platform startup bootstrap is disabled");
            return;
        }

        int attempts = Math.max(maxAttempts, 1);
        long delayMs = Math.max(retryDelayMs, 1000L);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (documentService.countDocuments() == 0) {
                DocumentService.DemoImportResult importResult = documentService.bootstrapDemoDocuments(false);
                log.info(
                        "Startup bootstrap imported {} sample documents and skipped {} existing documents",
                        importResult.getImported(),
                        importResult.getSkipped()
                );
            }

            DataInitializer.InitResult initResult = dataInitializer.initialize("all");
            int syncedChunks = documentService.syncVectorIndex();
            boolean sourcesReady = !documentService.search("炭疽病", 1).isEmpty();
            boolean startupReady = initResult.isKnowledgeGraphInitialized()
                    && initResult.isVectorStoreInitialized()
                    && sourcesReady;

            if (startupReady) {
                log.info(
                        "Platform startup bootstrap completed on attempt {} with {} searchable documents and {} indexed chunks",
                        attempt,
                        documentService.countDocuments(),
                        syncedChunks
                );
                return;
            }

            log.warn(
                    "Platform startup bootstrap attempt {}/{} is not ready yet. graphInitialized={}, vectorInitialized={}, sourcesReady={}, documents={}, indexedDocuments={}",
                    attempt,
                    attempts,
                    initResult.isKnowledgeGraphInitialized(),
                    initResult.isVectorStoreInitialized(),
                    sourcesReady,
                    documentService.countDocuments(),
                    documentService.countIndexedDocuments()
            );

            if (attempt < attempts) {
                sleep(delayMs);
            }
        }

        log.warn(
                "Platform startup bootstrap exhausted {} attempts. The platform can still run, but new environments may need a manual /system/demo/bootstrap call.",
                attempts
        );
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Platform startup bootstrap was interrupted");
        }
    }
}
