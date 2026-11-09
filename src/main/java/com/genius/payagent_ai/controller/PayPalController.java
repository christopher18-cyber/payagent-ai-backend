package com.genius.payagent_ai.controller;

import com.genius.payagent_ai.model.PayoutBatch;
import com.genius.payagent_ai.service.AgentClientService;
import com.genius.payagent_ai.service.PayPalAuthService;
import com.genius.payagent_ai.service.PayoutPersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


@RestController
@RequestMapping("/api/paypal")
public class PayPalController {
    @Autowired
    private PayPalAuthService payPalAuthService;

    @Autowired
    private AgentClientService agentClientService;

    @Autowired
    private PayoutPersistenceService payoutPersistenceService;

    @Value("${paypal.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/payout")
    public ResponseEntity<?> executePayout(@RequestBody Map<String, Object> payoutRequest) {
        try {
            // 1. Send request to your live Render Python LangGraph agent for AI risk auditing first
            Map<String, Object> agentEvaluation = agentClientService.evaluateRequestThroughAgent(payoutRequest);

            String approvalStatus = (String) agentEvaluation.get("approval_status");
            if ("REJECTED".equalsIgnoreCase(approvalStatus)) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", "Payout blocked by AI Agent guardrails",
                        "audit_details", agentEvaluation
                ));
            }

            // 2. If approved, proceed with PayPal Token & Payout Execution
            String accessToken = payPalAuthService.getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity request = new HttpEntity<>(payoutRequest, headers);

            ResponseEntity response = restTemplate.postForEntity(
                    baseUrl + "/v1/payments/payouts", request, Map.class
            );

            if(response.getStatusCode().is2xxSuccessful() && response.getBody() != null){
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

                PayoutBatch savedBatch = payoutPersistenceService.savePayoutResponse(responseBody);

                return ResponseEntity.status(response.getStatusCode()).body(savedBatch);
            }

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    @GetMapping("/payout/{batchId}")
    public ResponseEntity<?> checkPayoutStatus(@PathVariable String batchId){
        try{
            String accessToken = payPalAuthService.getAccessToken();
            Map statusResponse = payPalAuthService.getPayoutStatus(batchId,accessToken);
            return ResponseEntity.ok(statusResponse);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error",e.getMessage()));
        }
    }
}