package com.ayushi.DailyTaskManagementSystem.controller;

import com.ayushi.DailyTaskManagementSystem.model.Task;
import com.ayushi.DailyTaskManagementSystem.service.TaskToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class TaskToolController {

    private final TaskToolService taskToolService;

    @Autowired
    public TaskToolController(TaskToolService taskToolService) {
        this.taskToolService = taskToolService;
    }

    @PostMapping("/createTask")
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        Task created = taskToolService.createTask(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/getTasks")
    public List<Task> getTasks() {
        return taskToolService.getTasks();
    }

    @PostMapping("/{id}/completeTask")
    public Task completeTask(@PathVariable Long id) {
        return taskToolService.completeTask(id);
    }

    @PutMapping("/{id}/updateTask")
    public Task updateTask(@PathVariable Long id, @RequestBody Task task) {
        return taskToolService.updateTask(id, task);
    }

    @DeleteMapping("/{id}/deleteTask")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskToolService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
