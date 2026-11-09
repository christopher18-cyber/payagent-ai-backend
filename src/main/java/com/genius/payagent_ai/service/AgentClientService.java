package com.genius.payagent_ai.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AgentClientService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String agentUrl = "https://payagent-ai-agent.onrender.com/agent/evaluate";

    public Map<String,Object> evaluateRequestThroughAgent(Map<String,Object> payoutPayload){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> requests = new HttpEntity<>(payoutPayload, headers);

        try{
            ResponseEntity response = restTemplate.postForEntity(agentUrl,requests, Map.class);
            if(response.getStatusCode().is2xxSuccessful() && response.getBody() != null){
                return (Map<String, Object>) response.getBody();
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to communicate with Python Agent service: "+ e.getMessage());
        }

        throw new RuntimeException("Invalid response from Python Agent");
    }
}