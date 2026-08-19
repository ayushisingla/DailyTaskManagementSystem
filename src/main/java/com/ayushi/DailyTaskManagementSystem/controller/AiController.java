package com.ayushi.DailyTaskManagementSystem.controller;

import com.ayushi.DailyTaskManagementSystem.service.AiService;
import com.ayushi.DailyTaskManagementSystem.service.TaskToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final TaskToolService taskToolService;

    @Autowired
    public AiController(AiService aiService, TaskToolService taskToolService) {
        this.aiService = aiService;
        this.taskToolService = taskToolService;
    }

    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> sayHello() {
        String resp = aiService.sayHello();
        return ResponseEntity.ok(Map.of("response", resp));
    }

}
