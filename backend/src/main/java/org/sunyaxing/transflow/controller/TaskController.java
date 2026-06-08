package org.sunyaxing.transflow.controller;

import org.sunyaxing.transflow.engine.FlowEngineManager;
import org.sunyaxing.transflow.model.Flow;
import org.sunyaxing.transflow.model.Task;
import org.sunyaxing.transflow.persistence.FileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final FileRepository repository;
    private final FlowEngineManager engineManager;

    public TaskController(FileRepository repository, FlowEngineManager engineManager) {
        this.repository = repository;
        this.engineManager = engineManager;
    }

    @GetMapping
    public Mono<ResponseEntity<List<Task>>> list() {
        return Mono.fromCallable(() -> ResponseEntity.ok(repository.findAll()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Task>> get(@PathVariable String id) {
        return Mono.fromCallable(() ->
            repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build())
        );
    }

    @PostMapping
    public Mono<ResponseEntity<Task>> create(@RequestBody Task task) {
        return Mono.fromCallable(() -> {
            task.setId(UUID.randomUUID().toString().substring(0, 8));
            task.setCreatedAt(System.currentTimeMillis());
            task.setUpdatedAt(System.currentTimeMillis());
            if (task.getFlow() == null) {
                task.setFlow(new Flow(new ArrayList<>(), new ArrayList<>()));
            }
            Task saved = repository.save(task);

            // Auto-start the task
            engineManager.startTask(saved.getId(), saved.getFlow());

            return ResponseEntity.ok(saved);
        });
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Task>> update(@PathVariable String id, @RequestBody Task task) {
        return Mono.fromCallable(() ->
            repository.findById(id).map(existing -> {
                existing.setName(task.getName());
                existing.setDescription(task.getDescription());
                if (task.getFlow() != null) {
                    existing.setFlow(task.getFlow());
                }
                existing.setUpdatedAt(System.currentTimeMillis());
                Task saved = repository.save(existing);

                // Restart flow on update
                engineManager.stopTask(id);
                if (saved.getFlow() != null) {
                    engineManager.startTask(id, saved.getFlow());
                }

                return ResponseEntity.ok(saved);
            }).orElse(ResponseEntity.notFound().build())
        );
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return Mono.fromRunnable(() -> {
            engineManager.stopTask(id);
            repository.deleteById(id);
        }).then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}
