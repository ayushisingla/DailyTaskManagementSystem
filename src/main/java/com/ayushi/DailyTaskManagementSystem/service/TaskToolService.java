package com.ayushi.DailyTaskManagementSystem.service;

import com.ayushi.DailyTaskManagementSystem.model.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskToolService {

    private final TaskService taskService;

    @Autowired
    public TaskToolService(TaskService taskService) {
        this.taskService = taskService;
    }

    public Task createTask(Task task) {
        return taskService.createTask(task);
    }

    public List<Task> getTasks() {
        return taskService.getTasks();
    }

    public Task completeTask(Long id) {
        return taskService.completeTask(id);
    }

    public Task updateTask(Long id, Task task) {
        return taskService.updateTask(id, task);
    }

    public void deleteTask(Long id) {
        taskService.deleteTask(id);
    }
}
