package com.ayushi.DailyTaskManagementSystem.service;

import com.ayushi.DailyTaskManagementSystem.TaskRepository;
import com.ayushi.DailyTaskManagementSystem.model.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository repository;

    @Autowired
    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Task createTask(Task task) {
        if (task.getStatus() == null) {
            task.setStatus("PENDING");
        }
        // server-side validation: due date must not be in the past
        if (task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date cannot be in the past");
        }
        return repository.save(task);
    }

    public Task getTask(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public List<Task> getTasks() {
        return repository.findAll();
    }

    public List<Task> getTasksToday() {
        LocalDate today = LocalDate.now();
        return repository.findByDueDate(today);
    }

    public Task updateTask(Long id, Task updated) {
        // validate incoming due date first
        if (updated.getDueDate() != null && updated.getDueDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date cannot be in the past");
        }

        Task existing = getTask(id);
        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getPriority() != null) existing.setPriority(updated.getPriority());
        if (updated.getDueDate() != null) existing.setDueDate(updated.getDueDate());
        if (updated.getEstimatedMinutes() != null) existing.setEstimatedMinutes(updated.getEstimatedMinutes());
        return repository.save(existing);
    }

    public Task completeTask(Long id) {
        Task t = getTask(id);
        t.setStatus("COMPLETED");
        t.setCompletedAt(LocalDateTime.now());
        return repository.save(t);
    }

    public void deleteTask(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        repository.deleteById(id);
    }
}
