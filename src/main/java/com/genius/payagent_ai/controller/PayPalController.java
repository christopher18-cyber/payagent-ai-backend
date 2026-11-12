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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

    @SuppressWarnings("unchecked")
    @PostMapping("/payout")
    public ResponseEntity<?> executePayout(@RequestBody Map<String, Object> req) {
        try {
            // 1. AI audit first
            Map<String, Object> audit = agentClientService.evaluateRequestThroughAgent(req);
            String decision = String.valueOf(audit.get("approval_status")).toUpperCase();

            // Fail closed: money moves only on an explicit APPROVED
            if (!"APPROVED".equals(decision)) {
                String status = "REJECTED".equals(decision) ? "REJECTED" : "NEEDS_REVIEW";
                return ResponseEntity.ok(Map.of("status", status, "audit", audit));
            }

            // 2. Build PayPal's real payout payload
            String sendId = req.get("request_id") + "-" + System.currentTimeMillis();
            String amount = new BigDecimal(String.valueOf(req.get("amount")))
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
            String receiver = String.valueOf(req.get("vendor_email"));
            String description = String.valueOf(req.get("item_description"));

            Map<String, Object> paypalBody = Map.of(
                    "sender_batch_header", Map.of(
                            "sender_batch_id", sendId,
                            "email_subject", "You have a payout from PayAgent AI"),
                    "items", List.of(Map.of(
                            "recipient_type", "EMAIL",
                            "receiver", receiver,
                            "note", description,
                            "sender_item_id", sendId + "-1",
                            "amount", Map.of("value", amount, "currency", "USD"))));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(payPalAuthService.getAccessToken());

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v1/payments/payouts",
                    new HttpEntity<>(paypalBody, headers),
                    Map.class);

            Map<String, Object> header = (Map<String, Object>) response.getBody().get("batch_header");

            // 3. Save with the audit trail
            PayoutBatch saved = payoutPersistenceService.saveCreated(
                    (String) header.get("payout_batch_id"),
                    (String) header.get("batch_status"),
                    new BigDecimal(amount),
                    receiver,
                    description,
                    sendId + "-1",
                    audit);

            return ResponseEntity.ok(Map.of(
                    "status", "APPROVED_AND_SENT",
                    "audit", audit,
                    "batch_id", saved.getPayoutBatchId(),
                    "batch_status", saved.getBatchStatus(),
                    "amount", saved.getTotalAmount(),
                    "receiver", receiver));

        } catch (HttpStatusCodeException e) {
            // Pass PayPal's own error text through so sandbox problems are easy to diagnose
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "PayPal error: " + e.getResponseBodyAsString()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/payout/{batchId}")
    public ResponseEntity<?> checkPayoutStatus(@PathVariable String batchId) {
        try {
            String accessToken = payPalAuthService.getAccessToken();
            Map statusResponse = payPalAuthService.getPayoutStatus(batchId, accessToken);
            return ResponseEntity.ok(statusResponse);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}