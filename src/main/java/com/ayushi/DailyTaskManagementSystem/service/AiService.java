package com.ayushi.DailyTaskManagementSystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final RestTemplate restTemplate;

    @Autowired
    public AiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String sayHello() {
        return generate("Say hello.");
    }

    public String generate(String prompt) {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("LLM_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY or LLM_API_KEY environment variable is not set");
        }

        String url = System.getenv("GROQ_API_URL");
        if (url == null || url.isBlank()) {
            url = "https://api.groq.com/openai/v1/chat/completions";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String model = System.getenv("GROQ_MODEL");
        if (model == null || model.isBlank()) {
            model = "openai/gpt-oss-120b";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        body.put("messages", messages);

        // Allow a mock response for offline testing
        String mock = System.getenv("GROQ_MOCK_RESPONSE");
        if (mock != null && !mock.isBlank()) {
            return mock;
        }

        // If no API key is configured, return a safe generated mock based on the prompt
        if (apiKey == null || apiKey.isBlank()) {
            // Heuristic mock: try to infer due date and hours
            String lowerPrompt = prompt.toLowerCase();
            String dueDate = "";
            if (lowerPrompt.contains("tomorrow")) dueDate = "tomorrow";
            else if (lowerPrompt.contains("today")) dueDate = "today";

            int minutes = 0;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*hour").matcher(lowerPrompt);
            if (m.find()) {
                try { minutes = Integer.parseInt(m.group(1)) * 60; } catch (Exception ignored) {}
            } else {
                m = java.util.regex.Pattern.compile("(\\d+)\\s*min").matcher(lowerPrompt);
                if (m.find()) {
                    try { minutes = Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
                }
            }

            String title = prompt;
            if (title.length() > 120) title = title.substring(0,120);

            String generated = String.format("{\"action\":\"createTask\",\"title\":\"%s\",\"dueDate\":\"%s\",\"estimatedMinutes\":%d}",
                    title.replaceAll("\"","\\\""), dueDate, minutes);
            return generated;
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            throw new IllegalStateException("LLM request failed (network): " + e.getMessage(), e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("LLM request failed: " + response.getStatusCode());
        }

        String bodyStr = response.getBody();
        if (bodyStr == null) return "";

        try {
            JsonNode root = new ObjectMapper().readTree(bodyStr);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText();
            }
            JsonNode generatedText = root.path("generated_text");
            if (generatedText.isTextual()) {
                return generatedText.asText();
            }
            JsonNode output = root.path("output");
            if (output.isTextual()) {
                return output.asText();
            }
        } catch (Exception ignored) {
            // Fall through to legacy extraction logic below.
        }

        // attempt to extract useful text fields
        String lower = bodyStr.toLowerCase();
        int i;
        i = lower.indexOf("\"generated_text\"");
        if (i >= 0) return extractStringValue(bodyStr, i);
        i = lower.indexOf("\"text\"");
        if (i >= 0) return extractStringValue(bodyStr, i);
        i = lower.indexOf("\"output\"");
        if (i >= 0) return extractStringValue(bodyStr, i);
        i = lower.indexOf("\"outputs\"");
        if (i >= 0) return extractStringValue(bodyStr, i);

        // fallback: return entire body
        return bodyStr;
    }

    private String extractStringValue(String s, int keyIndex) {
        int colon = s.indexOf(':', keyIndex);
        if (colon < 0) return s;
        int quoteStart = s.indexOf('"', colon);
        if (quoteStart < 0) return s;
        int quoteEnd = s.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return s;
        String content = s.substring(quoteStart + 1, quoteEnd);
        content = content.replaceAll("\\n", "\n").replaceAll("\\\"", "\"");
        return content;
    }
}
