package org.sunyaxing.transflow.persistence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.sunyaxing.transflow.model.Task;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class FileRepository {

    private static final String DB_DIR = "db";
    private static final String TASKS_FILE = DB_DIR + "/tasks.json";

    public FileRepository() {
        try {
            Files.createDirectories(Paths.get(DB_DIR));
            if (!Files.exists(Paths.get(TASKS_FILE))) {
                Files.writeString(Paths.get(TASKS_FILE), "[]");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file repository", e);
        }
    }

    public List<Task> findAll() {
        try {
            String content = Files.readString(Paths.get(TASKS_FILE));
            return JSON.parseArray(content, Task.class);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public Optional<Task> findById(String id) {
        return findAll().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst();
    }

    public Task save(Task task) {
        List<Task> tasks = findAll();
        Optional<Task> existing = tasks.stream()
                .filter(t -> t.getId().equals(task.getId()))
                .findFirst();
        if (existing.isPresent()) {
            tasks.replaceAll(t -> t.getId().equals(task.getId()) ? task : t);
        } else {
            tasks.add(task);
        }
        writeAll(tasks);
        return task;
    }

    public void deleteById(String id) {
        List<Task> tasks = findAll();
        tasks.removeIf(t -> t.getId().equals(id));
        writeAll(tasks);
    }

    private void writeAll(List<Task> tasks) {
        try {
            String json = JSON.toJSONString(tasks, JSONWriter.Feature.PrettyFormat);
            Files.writeString(Paths.get(TASKS_FILE), json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write tasks", e);
        }
    }
}
