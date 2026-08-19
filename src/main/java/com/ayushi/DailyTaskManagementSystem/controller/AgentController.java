package com.ayushi.DailyTaskManagementSystem.controller;

import com.ayushi.DailyTaskManagementSystem.model.Task;
import com.ayushi.DailyTaskManagementSystem.service.AiService;
import com.ayushi.DailyTaskManagementSystem.service.TaskToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AiService aiService;
    private final TaskToolService taskToolService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AgentController(
            AiService aiService,
            TaskToolService taskToolService,
            ObjectMapper objectMapper) {

        this.aiService = aiService;
        this.taskToolService = taskToolService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/interpret")
    public ResponseEntity<Map<String, Object>> interpretAndAct(
            @RequestBody Map<String, String> body) {

        String text = body.getOrDefault("text", "").trim();

        if (text.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "text is required"));
        }

        /*
         * ---------------------------------------------------------
         * 1. Tell the LLM what actions our agent supports
         * ---------------------------------------------------------
         */

        String instruction = """
                You are the action interpreter for a personal task management application.

                Your job is to understand the user's request and return exactly ONE valid JSON object.

                The supported actions are:

                1. createTask
                Use when the user wants to create, add, remember, schedule, or track a new task.

                2. updateTask
                Use when the user wants to change an existing task.

                3. deleteTask
                Use when the user wants to delete or remove an existing task.

                4. completeTask
                Use when the user wants to mark an existing task as completed, done, finished, or complete.

                5. listTasks
                Use when the user wants to see, show, list, or find their existing tasks.

                6. none
                Use when the user is not asking the task manager to perform an action.

                --------------------------------------------------
                CREATE TASK
                --------------------------------------------------

                For createTask return:

                {
                  "action": "createTask",
                  "title": "<short task title>",
                  "dueDate": "<today, tomorrow, yyyy-MM-dd, or empty>",
                  "estimatedMinutes": <integer>
                }

                Examples:

                "Buy milk"
                =>
                {"action":"createTask","title":"Buy milk","dueDate":"","estimatedMinutes":0}

                "I need to do laundry today"
                =>
                {"action":"createTask","title":"Do laundry","dueDate":"today","estimatedMinutes":0}

                "Study Java for 2 hours tomorrow"
                =>
                {"action":"createTask","title":"Study Java","dueDate":"tomorrow","estimatedMinutes":120}

                "Buy groceries by 2026-08-25"
                =>
                {"action":"createTask","title":"Buy groceries","dueDate":"2026-08-25","estimatedMinutes":0}

                --------------------------------------------------
                UPDATE TASK
                --------------------------------------------------

                For updateTask return:

                {
                  "action": "updateTask",
                  "taskQuery": "<existing task title or identifying phrase>",
                  "title": "<new title or empty>",
                  "dueDate": "<today, tomorrow, yyyy-MM-dd, or empty>",
                  "estimatedMinutes": <integer or 0>,
                  "priority": "<new priority or empty>",
                  "description": "<new description or empty>"
                }

                Only include values that the user wants to change.
                Use taskQuery to identify the existing task.

                Example:

                "Change buy milk to buy almond milk"
                =>
                {"action":"updateTask","taskQuery":"buy milk","title":"Buy almond milk","dueDate":"","estimatedMinutes":0,"priority":"","description":""}

                "Move buy milk to tomorrow"
                =>
                {"action":"updateTask","taskQuery":"buy milk","title":"","dueDate":"tomorrow","estimatedMinutes":0,"priority":"","description":""}

                --------------------------------------------------
                DELETE TASK
                --------------------------------------------------

                For deleteTask return:

                {
                  "action": "deleteTask",
                  "taskQuery": "<existing task title or identifying phrase>"
                }

                Example:

                "Delete buy milk"
                =>
                {"action":"deleteTask","taskQuery":"buy milk"}

                --------------------------------------------------
                COMPLETE TASK
                --------------------------------------------------

                For completeTask return:

                {
                  "action": "completeTask",
                  "taskQuery": "<existing task title or identifying phrase>"
                }

                Example:

                "Mark buy milk as complete"
                =>
                {"action":"completeTask","taskQuery":"buy milk"}

                --------------------------------------------------
                LIST TASKS
                --------------------------------------------------

                For listTasks return:

                {
                  "action": "listTasks",
                  "filter": "<all, today, tomorrow, overdue>"
                }

                Examples:

                "Show me my tasks"
                =>
                {"action":"listTasks","filter":"all"}

                "What do I need to do today?"
                =>
                {"action":"listTasks","filter":"today"}

                "Show tomorrow's tasks"
                =>
                {"action":"listTasks","filter":"tomorrow"}

                "What tasks are overdue?"
                =>
                {"action":"listTasks","filter":"overdue"}

                --------------------------------------------------
                IMPORTANT RULES
                --------------------------------------------------

                A direct imperative request is a task.

                Examples:
                "Buy milk" => createTask
                "Call Mom" => createTask
                "Study Java" => createTask
                "Finish the report" => createTask

                A question about how to perform something is NOT a task.

                "How do I fix the login bug?"
                => {"action":"none"}

                "How do I learn Spring Boot?"
                => {"action":"none"}

                A task action must be explicit.

                "Buy milk" => createTask
                "Can you explain how to buy milk?"
                => none

                For createTask, extract a concise title.
                Remove phrases such as:
                "I need to"
                "I have to"
                "I want to"
                "please"
                "remind me to"
                "add to my tasks"

                For dueDate:

                Allowed values are ONLY:
                - today
                - tomorrow
                - yyyy-MM-dd
                - empty

                Do not output weekday names such as Friday.
                Do not invent dates.

                For estimatedMinutes:

                Only use a duration explicitly stated by the user.

                "2 hours" = 120
                "30 minutes" = 30
                "1.5 hours" = 90

                If no duration is given, use 0.
                Never guess the duration.

                For updateTask:
                Use empty values for fields that should remain unchanged.

                Only output one valid JSON object.
                Do not output markdown.
                Do not output explanations.
                """;

        String prompt = instruction + "\n\nUser request:\n" + text;

        /*
         * ---------------------------------------------------------
         * 2. Ask the LLM to interpret the request
         * ---------------------------------------------------------
         */

        String aiResp;

        try {
            aiResp = aiService.generate(prompt);
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "LLM request failed",
                    "detail", e.getMessage()
            ));
        }

        /*
         * ---------------------------------------------------------
         * 3. Parse JSON
         * ---------------------------------------------------------
         */

        JsonNode parsed;

        try {
            parsed = parseJsonResponse(aiResp);
            // Attach original user text so handlers can use heuristics if the model omits fields
            if (parsed.isObject()) {
                ((ObjectNode) parsed).put("userText", text);
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "action", "none",
                    "message", "Could not understand the AI response",
                    "aiResponse", aiResp
            ));
        }

        String action = parsed.path("action").asText("none");

        /*
         * ---------------------------------------------------------
         * 4. Execute the requested action
         * ---------------------------------------------------------
         */

        ResponseEntity<Map<String, Object>> resp;
        switch (action.toLowerCase()) {
            case "createtask":
                resp = handleCreateTask(parsed);
                break;
            case "updatetask":
                resp = handleUpdateTask(parsed);
                break;
            case "deletetask":
                resp = handleDeleteTask(parsed);
                break;
            case "completetask":
                resp = handleCompleteTask(parsed);
                break;
            case "listtasks":
                resp = handleListTasks(parsed);
                break;
            default:
                resp = ResponseEntity.ok(Map.of(
                        "action", "none",
                        "message", "No task action required"
                ));
        }

        boolean debug = false;
        try { debug = Boolean.parseBoolean(System.getenv("GROQ_DEBUG")); } catch (Exception ignored) {}
        if (debug) {
            Map<String, Object> respBody = new HashMap<>();
            if (resp.getBody() != null) respBody.putAll(resp.getBody());
            respBody.put("aiResponse", aiResp);
            return ResponseEntity.status(resp.getStatusCode()).body(respBody);
        }
        return resp;
    }

    /*
     * ============================================================
     * CREATE TASK
     * ============================================================
     */

    private ResponseEntity<Map<String, Object>> handleCreateTask(
            JsonNode parsed) {

        String title = parsed.path("title").asText("").trim();

        if (title.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "action", "none",
                    "message", "Task title is missing"
            ));
        }

        // Prevent duplicate tasks: if an existing task matches the title, return a helpful message
        Task existing = findTask(title);
        if (existing != null) {
            return ResponseEntity.ok(Map.of(
                    "action", "none",
                    "message", "Task already exists",
                    "existingTask", existing
            ));
        }

        Task task = new Task();
        task.setTitle(title);
        task.setDescription("Created by AI");
        task.setStatus("PENDING");

        String dueDate = parsed.path("dueDate").asText("");
        LocalDate resolvedDate = resolveDate(dueDate);
        if (resolvedDate != null) {
            task.setDueDate(resolvedDate);
        }

        int estimatedMinutes = parsed.path("estimatedMinutes").asInt(0);
        // If model didn't provide estimatedMinutes, try to extract from title or user text
        if (estimatedMinutes <= 0) {
            String textForEstimate = "";
            if (title != null) textForEstimate += title + " ";
            textForEstimate += parsed.path("userText").asText("");

            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(hours|hour|hrs|hr|h)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(textForEstimate);
            if (mm.find()) {
                try {
                    double hours = Double.parseDouble(mm.group(1));
                    estimatedMinutes = (int) Math.round(hours * 60);
                } catch (Exception ignored) {}
            } else {
                mm = java.util.regex.Pattern.compile("(\\d+)\\s*(minutes|minute|mins|min|m)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(textForEstimate);
                if (mm.find()) {
                    try { estimatedMinutes = Integer.parseInt(mm.group(1)); } catch (Exception ignored) {}
                }
            }
        }
        if (estimatedMinutes > 0) {
            task.setEstimatedMinutes(estimatedMinutes);
        }

        // allow AI to set priority (LOW, MEDIUM, HIGH)
        String priority = parsed.path("priority").asText("").trim();
        if (!priority.isBlank()) {
            task.setPriority(priority.toUpperCase());
        } else {
            // heuristic: infer priority from title or original user text keywords
            String titleForPriority = title != null ? title.toLowerCase() : "";
            String userTextForPriority = parsed.path("userText").asText("").toLowerCase();
            String combined = (titleForPriority + " " + userTextForPriority).trim();

            if (combined.contains("not important") || combined.contains("not urgent") || combined.contains("unimportant") || combined.contains("low priority") || combined.contains("low")) {
                task.setPriority("LOW");
            } else if (combined.contains("urgent") || combined.contains("important") || combined.contains("high priority") || combined.contains("high")) {
                task.setPriority("HIGH");
            } else if (combined.contains("medium")) {
                task.setPriority("MEDIUM");
            }
        }

        // allow AI-provided description
        String desc = parsed.path("description").asText("").trim();
        if (!desc.isBlank()) {
            task.setDescription(desc);
        }

        Task created = taskToolService.createTask(task);
        return ResponseEntity.ok(Map.of(
                "action", "createTask",
                "task", created
        ));
    }

    /*
     * ============================================================
     * UPDATE TASK
     * ============================================================
     */

    private ResponseEntity<Map<String, Object>> handleUpdateTask(
            JsonNode parsed) {

        String taskQuery =
                parsed.path("taskQuery").asText("").trim();

        if (taskQuery.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "action", "updateTask",
                    "message", "Which task should be updated?"
            ));
        }

        Task existing = findTask(taskQuery);

        if (existing == null) {
            return ResponseEntity.ok(Map.of(
                    "action", "updateTask",
                    "message", "Could not find task: " + taskQuery
            ));
        }

        String newTitle =
                parsed.path("title").asText("").trim();

        if (!newTitle.isEmpty()) {
            existing.setTitle(newTitle);
        }

        String dueDate =
                parsed.path("dueDate").asText("").trim();

        if (!dueDate.isEmpty()) {
            LocalDate resolvedDate = resolveDate(dueDate);

            if (resolvedDate != null) {
                existing.setDueDate(resolvedDate);
            }
        }

        int estimatedMinutes =
                parsed.path("estimatedMinutes").asInt(0);

        if (estimatedMinutes > 0) {
            existing.setEstimatedMinutes(estimatedMinutes);
        }

        String priority =
                parsed.path("priority").asText("").trim();

        if (!priority.isEmpty()) {
            existing.setPriority(priority);
        }

        String description =
                parsed.path("description").asText("").trim();

        if (!description.isEmpty()) {
            existing.setDescription(description);
        }

        Task updated =
                taskToolService.updateTask(existing.getId(), existing);

        return ResponseEntity.ok(Map.of(
                "action", "updateTask",
                "task", updated
        ));
    }

    /*
     * ============================================================
     * DELETE TASK
     * ============================================================
     */

    private ResponseEntity<Map<String, Object>> handleDeleteTask(
            JsonNode parsed) {

        String taskQuery =
                parsed.path("taskQuery").asText("").trim();

        if (taskQuery.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "action", "deleteTask",
                    "message", "Which task should be deleted?"
            ));
        }

        Task existing = findTask(taskQuery);

        if (existing == null) {
            return ResponseEntity.ok(Map.of(
                    "action", "deleteTask",
                    "message", "Could not find task: " + taskQuery
            ));
        }

        // For safety: do NOT perform destructive delete actions automatically from AI.
        // Require the user to delete the task manually from the UI.
        Map<String, Object> resp = new HashMap<>();
        resp.put("action", "deleteTask");
        resp.put("message", "Deletion is disabled via AI for safety. Please delete the task manually.");
        // include minimal matched info but avoid using the key name 'task' so the frontend doesn't treat this as a created task
        resp.put("matchedTask", Map.of("id", existing.getId(), "title", existing.getTitle()));
        return ResponseEntity.ok(resp);
    }

    /*
     * ============================================================
     * COMPLETE TASK
     * ============================================================
     */

    private ResponseEntity<Map<String, Object>> handleCompleteTask(
            JsonNode parsed) {

        String taskQuery =
                parsed.path("taskQuery").asText("").trim();

        if (taskQuery.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "action", "completeTask",
                    "message", "Which task should be completed?"
            ));
        }

        Task existing = findTask(taskQuery);

        if (existing == null) {
            return ResponseEntity.ok(Map.of(
                    "action", "completeTask",
                    "message", "Could not find task: " + taskQuery
            ));
        }

        Task completed =
                taskToolService.completeTask(existing.getId());

        return ResponseEntity.ok(Map.of(
                "action", "completeTask",
                "task", completed
        ));
    }

    /*
     * ============================================================
     * LIST TASKS
     * ============================================================
     */

    private ResponseEntity<Map<String, Object>> handleListTasks(
            JsonNode parsed) {

        String filter =
                parsed.path("filter").asText("all").toLowerCase();

        List<Task> tasks =
                taskToolService.getTasks();

        LocalDate today = LocalDate.now();

        List<Task> filteredTasks;

        switch (filter) {

            case "today":

                filteredTasks = tasks.stream()
                        .filter(task ->
                                task.getDueDate() != null &&
                                        task.getDueDate().equals(today))
                        .collect(Collectors.toList());

                break;

            case "tomorrow":

                LocalDate tomorrow =
                        today.plusDays(1);

                filteredTasks = tasks.stream()
                        .filter(task ->
                                task.getDueDate() != null &&
                                        task.getDueDate().equals(tomorrow))
                        .collect(Collectors.toList());

                break;

            case "overdue":

                filteredTasks = tasks.stream()
                        .filter(task ->
                                task.getDueDate() != null &&
                                        task.getDueDate().isBefore(today) &&
                                        !"COMPLETED".equalsIgnoreCase(task.getStatus()))
                        .collect(Collectors.toList());

                break;

            case "all":

            default:

                filteredTasks = tasks;
        }

        return ResponseEntity.ok(Map.of(
                "action", "listTasks",
                "filter", filter,
                "count", filteredTasks.size(),
                "tasks", filteredTasks
        ));
    }

    /*
     * ============================================================
     * FIND EXISTING TASK
     * ============================================================
     *
     * This is important.
     *
     * The LLM does NOT need to know the database ID.
     *
     * User says:
     *
     * "Mark buy milk as complete"
     *
     * LLM says:
     *
     * taskQuery = "buy milk"
     *
     * We find the real database task here.
     */

    private Task findTask(String query) {

        if (query == null || query.isBlank()) {
            return null;
        }

        String normalizedQuery =
                normalize(query);

        List<Task> tasks =
                taskToolService.getTasks();

        /*
         * First try exact title match.
         */

        for (Task task : tasks) {

            if (task.getTitle() == null) {
                continue;
            }

            if (normalize(task.getTitle())
                    .equals(normalizedQuery)) {

                return task;
            }
        }

        /*
         * Then try title containing the query.
         */

        List<Task> matches = tasks.stream()
                .filter(task -> task.getTitle() != null)
                .filter(task ->
                        normalize(task.getTitle())
                                .contains(normalizedQuery))
                .toList();

        /*
         * If there is exactly one partial match,
         * use it.
         */

        if (matches.size() == 1) {
            return matches.get(0);
        }

        /*
         * If multiple tasks match, don't randomly
         * modify/delete/complete one.
         */

        return null;
    }

    private String normalize(String value) {

        return value
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", " ");
    }

    /*
     * ============================================================
     * DATE RESOLUTION
     * ============================================================
     */

    private LocalDate resolveDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.trim().toLowerCase();

        if ("today".equals(normalized)) {
            return LocalDate.now();
        }

        if ("tomorrow".equals(normalized)) {
            return LocalDate.now().plusDays(1);
        }

        try {
            return LocalDate.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    /*
     * ============================================================
     * JSON PARSING
     * ============================================================
     */

    private JsonNode parseJsonResponse(String response)
            throws Exception {

        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException(
                    "Empty response from LLM");
        }

        /*
         * First try parsing the entire response.
         */

        try {
            return objectMapper.readTree(response);
        } catch (Exception ignored) {
        }

        /*
         * If model returned something like:
         *
         * Sure! Here is the JSON:
         * {"action":"createTask",...}
         *
         * extract the first JSON object.
         */

        String json = extractFirstJson(response);

        if (json == null) {
            throw new IllegalArgumentException(
                    "No JSON object found");
        }

        return objectMapper.readTree(json);
    }

    private String extractFirstJson(String text) {

        if (text == null) {
            return null;
        }

        int start = text.indexOf('{');

        if (start < 0) {
            return null;
        }

        int depth = 0;

        for (int i = start; i < text.length(); i++) {

            char c = text.charAt(i);

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }

            if (depth == 0) {
                return text.substring(start, i + 1);
            }
        }

        return null;
    }
}