package com.genius.payagent_ai.controller;

import com.genius.payagent_ai.service.AgentClientService;
import com.genius.payagent_ai.service.PayoutOrchestrationService;
import com.genius.payagent_ai.service.PayoutOrchestrationService.SubmitResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentRunController {

    @Autowired
    private AgentClientService agentClient;

    @Autowired
    private PayoutOrchestrationService service;

    @SuppressWarnings("unchecked")
    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody Map<String, Object> body) {
        if (!(body.get("invoices") instanceof List<?> invoices) || invoices.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one invoice");
        }

        // 1. The accounts-payable agent reads the invoices and proposes payments
        Map<String, Object> plan = agentClient.procure(Map.of("invoices", invoices));

        Object raw = plan.get("payments");
        List<Map<String, Object>> payments = raw instanceof List<?> l
                ? l.stream().map(o -> (Map<String, Object>) o).toList()
                : List.of();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("agent", Map.of(
                "summary", String.valueOf(plan.getOrDefault("summary", "")),
                "mode", String.valueOf(plan.getOrDefault("mode", "llm")),
                "paymentsProposed", payments.size()));

        if (payments.isEmpty()) {
            out.put("results", List.of());
            return ResponseEntity.ok(out);
        }

        // 2. Every proposed payment goes through PayAgent before any money moves
        SubmitResult result = service.submit(payments);
        out.put("batch_id", result.batchId());
        out.put("results", result.results());
        return ResponseEntity.ok(out);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception e) {
        return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}