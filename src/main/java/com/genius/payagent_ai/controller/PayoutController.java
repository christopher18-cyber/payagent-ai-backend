package com.genius.payagent_ai.controller;

import com.genius.payagent_ai.model.SpendingRules;
import com.genius.payagent_ai.service.PayoutOrchestrationService;
import com.genius.payagent_ai.service.PayoutOrchestrationService.ItemView;
import com.genius.payagent_ai.service.PayoutOrchestrationService.SubmitResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PayoutController {

    @Autowired
    private PayoutOrchestrationService service;

    @SuppressWarnings("unchecked")
    @PostMapping({"/payouts", "/paypal/payout"})
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> requests = body.get("requests") instanceof List<?> list
                ? list.stream().map(o -> (Map<String, Object>) o).toList()
                : List.of(body);

        SubmitResult result = service.submit(requests);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batch_id", result.batchId());
        out.put("results", result.results());

        if (requests.size() == 1) {
            ItemView v = result.results().get(0);
            String status = switch (String.valueOf(v.status())) {
                case "REJECTED" -> "REJECTED";
                case "AWAITING_REVIEW" -> "NEEDS_REVIEW";
                case "PAYMENT_FAILED" -> "PAYMENT_FAILED";
                default -> "APPROVED_AND_SENT";
            };
            out.put("status", status);
            out.put("audit", Map.of(
                    "risk_score", v.riskScore() == null ? 0.0 : v.riskScore(),
                    "audit_reason", v.reason() == null ? "" : v.reason()));
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/items")
    public List<ItemView> ledger() {
        return service.ledger();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/rules")
    public SpendingRules getRules() {
        return service.getRules();
    }

    @PutMapping("/rules")
    public SpendingRules updateRules(@RequestBody SpendingRules rules) {
        return service.updateRules(rules);
    }

    @PostMapping("/items/{id}/approve")
    public ItemView approve(@PathVariable String id) {
        return service.approve(id);
    }

    @PostMapping("/items/{id}/reject")
    public ItemView reject(@PathVariable String id) {
        return service.reject(id);
    }

    @PostMapping("/payouts/refresh")
    public Map<String, Object> refresh() {
        return Map.of("updated", service.refreshStatuses());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception e) {
        return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}