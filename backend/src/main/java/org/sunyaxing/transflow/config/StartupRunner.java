package org.sunyaxing.transflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.engine.FlowEngineManager;
import org.sunyaxing.transflow.persistence.FileRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final FileRepository repository;
    private final FlowEngineManager engineManager;

    public StartupRunner(FileRepository repository, FlowEngineManager engineManager) {
        this.repository = repository;
        this.engineManager = engineManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting auto-recovery for all tasks...");
        repository.findAll().forEach(task -> {
            if (task.getFlow() != null) {
                try {
                    engineManager.startTask(task.getId(), task.getFlow());
                    log.info("Recovered task: {} ({})", task.getName(), task.getId());
                } catch (Exception e) {
                    log.error("Failed to recover task {}: {}", task.getId(), e.getMessage());
                }
            }
        });
        log.info("Auto-recovery complete");
    }
}
