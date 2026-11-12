package com.genius.payagent_ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class PayPalPayoutService {

    public record PayoutLine(String senderItemId, String receiver, String amount, String note) {}

    @Autowired
    private PayPalAuthService auth;

    @Value("${paypal.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public Map<String, Object> createBatch(String senderBatchId, List<PayoutLine> lines) {
        List<Map<String, Object>> items = lines.stream().map(l -> Map.<String, Object>of(
                "recipient_type", "EMAIL",
                "receiver", l.receiver(),
                "note", l.note(),
                "sender_item_id", l.senderItemId(),
                "amount", Map.of("value", l.amount(), "currency", "USD")
        )).toList();

        Map<String, Object> body = Map.of(
                "sender_batch_header", Map.of(
                        "sender_batch_id", senderBatchId,
                        "email_subject", "You have a payout from PayAgent AI"),
                "items", items);
 
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(auth.getAccessToken());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/v1/payments/payouts", new HttpEntity<>(body, headers), Map.class);

        return (Map<String, Object>) response.getBody().get("batch_header");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchBatch(String payoutBatchId) {
        return (Map<String, Object>) auth.getPayoutStatus(payoutBatchId, auth.getAccessToken());
    }
}