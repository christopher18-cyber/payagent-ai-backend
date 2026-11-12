package com.genius.payagent_ai.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AgentClientService {

    private static final String AGENT_BASE = "https://payagent-ai-agent.onrender.com";

    private final RestTemplate restTemplate;

    public AgentClientService() {
        // Render's free tier can take about a minute to wake up, so allow for it
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(90_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public Map<String, Object> evaluateRequestThroughAgent(Map<String, Object> payload) {
        return post("/agent/evaluate", payload);
    }

    public Map<String, Object> procure(Map<String, Object> payload) {
        return post("/agent/procure", payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> request = new HttpEntity<>(payload, headers);

        String lastError = "unknown error";
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(AGENT_BASE + path, request, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return (Map<String, Object>) response.getBody();
                }
                lastError = "Invalid response from Python Agent";
            } catch (HttpStatusCodeException e) {
                int code = e.getStatusCode().value();
                lastError = code + " from the agent service";
                // 429 and 502/503/504 usually mean the free-tier server is waking up
                if (code != 429 && code != 502 && code != 503 && code != 504) {
                    throw new RuntimeException("Failed to communicate with Python Agent service: " + e.getMessage());
                }
            } catch (Exception e) {
                lastError = String.valueOf(e.getMessage());
            }

            if (attempt < 4) {
                try {
                    Thread.sleep(6000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new RuntimeException("Failed to communicate with Python Agent service after retries: " + lastError);
    }
}