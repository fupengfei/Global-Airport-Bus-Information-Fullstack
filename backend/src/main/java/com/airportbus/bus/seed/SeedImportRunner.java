package com.airportbus.bus.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** D4:SEED_ENABLED=true 时启动幂等导入。重跑安全。 */
@Component
@ConditionalOnProperty(name = "airportbus.seed.enabled", havingValue = "true")
public class SeedImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedImportRunner.class);
    private final SeedImporter importer;

    public SeedImportRunner(SeedImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Seed import starting (idempotent)...");
        importer.importFromClasspath("data/data.json");
        log.info("Seed import done.");
    }
}
