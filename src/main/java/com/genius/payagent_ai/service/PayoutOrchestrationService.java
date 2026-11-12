package com.genius.payagent_ai.service;


import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.genius.payagent_ai.model.PayoutBatch;
import com.genius.payagent_ai.model.PayoutItem;
import com.genius.payagent_ai.model.SpendingRules;
import com.genius.payagent_ai.repository.PayoutBatchRepository;
import com.genius.payagent_ai.repository.PayoutItemRepository;
import com.genius.payagent_ai.repository.SpendingRulesRepository;
import com.genius.payagent_ai.service.PayPalPayoutService.PayoutLine;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class PayoutOrchestrationService {

    private static final Set<String> UNPAID = Set.of("REJECTED", "AWAITING_REVIEW", "REVIEW_REJECTED", "PAYMENT_FAILED");
    private static final TypeReference<List<Map<String, Object>>> STEP_LIST = new TypeReference<>() {};

    public record ItemView(String id, String batchId, String requestId, String receiver, BigDecimal amount,
                           String description, String verdict, Double riskScore, String reason, String status,
                           List<String> flags, Object steps, String paypalBatchId, Instant createdAt) {}

    public record SubmitResult(String batchId, List<ItemView> results) {}

    private final PayoutBatchRepository batchRepo;
    private final PayoutItemRepository itemRepo;
    private final SpendingRulesRepository rulesRepo;
    private final AgentClientService agent;
    private final PayPalPayoutService paypal;
    private final ObjectMapper mapper;

    public PayoutOrchestrationService(PayoutBatchRepository batchRepo, PayoutItemRepository itemRepo,
                                      SpendingRulesRepository rulesRepo, AgentClientService agent,
                                      PayPalPayoutService paypal, ObjectMapper mapper) {
        this.batchRepo = batchRepo;
        this.itemRepo = itemRepo;
        this.rulesRepo = rulesRepo;
        this.agent = agent;
        this.paypal = paypal;
        this.mapper = mapper;
    }

    // ---------- Rules ----------

    public SpendingRules getRules() {
        return rulesRepo.findById(1L).orElseGet(() -> rulesRepo.save(SpendingRules.builder()
                .id(1L)
                .maxPerPayout(new BigDecimal("500"))
                .monthlyBudget(new BigDecimal("5000"))
                .newRecipientLimit(new BigDecimal("200"))
                .blockedKeywords("gift card,crypto,bitcoin,western union,lottery,casino")
                .build()));
    }

    public SpendingRules updateRules(SpendingRules in) {
        SpendingRules r = getRules();
        if (in.getMaxPerPayout() != null) r.setMaxPerPayout(in.getMaxPerPayout());
        if (in.getMonthlyBudget() != null) r.setMonthlyBudget(in.getMonthlyBudget());
        if (in.getNewRecipientLimit() != null) r.setNewRecipientLimit(in.getNewRecipientLimit());
        if (in.getBlockedKeywords() != null) r.setBlockedKeywords(in.getBlockedKeywords());
        return rulesRepo.save(r);
    }

    // ---------- Submit ----------

    public SubmitResult submit(List<Map<String, Object>> requests) {
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("No payout requests provided");
        if (requests.size() > 50) throw new IllegalArgumentException("Maximum 50 payouts per batch");

        SpendingRules rules = getRules();
        PayoutBatch batch = batchRepo.save(PayoutBatch.builder()
                .payoutBatchId("BATCH-" + shortId())
                .batchStatus("SUBMITTED")
                .currency("USD")
                .totalAmount(BigDecimal.ZERO)
                .timeCreated(Instant.now())
                .build());

        List<PayoutItem> all = new ArrayList<>();
        List<PayoutItem> approved = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map<String, Object> r : requests) {
            PayoutItem item = evaluateAndSave(batch, r, rules);
            all.add(item);
            total = total.add(item.getAmount());
            if ("APPROVED".equals(item.getTransactionStatus())) approved.add(item);
        }

        if (!approved.isEmpty()) sendToPayPal(approved);

        batch.setTotalAmount(total);
        batch.setBatchStatus("PROCESSED");
        batchRepo.save(batch);

        return new SubmitResult(batch.getPayoutBatchId(), all.stream().map(this::toView).toList());
    }

    private PayoutItem evaluateAndSave(PayoutBatch batch, Map<String, Object> r, SpendingRules rules) {
        String requestId = str(r.get("request_id"), "REQ-" + shortId());
        String description = str(r.get("item_description"), "");
        String email = str(r.get("vendor_email"), "").trim();
        BigDecimal amount = parseAmount(r.get("amount"));

        PayoutItem item = PayoutItem.builder()
                .payoutItemId("ITEM-" + shortId())
                .payoutBatch(batch)
                .requestId(requestId)
                .receiverEmail(email)
                .description(truncate(description, 1000))
                .amount(amount == null ? BigDecimal.ZERO : amount)
                .feeAmount(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .build();
        item.setSenderItemId(item.getPayoutItemId());

        boolean validEmail = email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        if (amount == null || amount.signum() <= 0 || !validEmail) {
            item.setApprovalStatus("REJECTED");
            item.setTransactionStatus("REJECTED");
            item.setRiskScore(1.0);
            item.setAuditReason("Invalid amount or email address.");
            item.setFlags("INVALID_INPUT");
            item.setStepsJson(toJson(List.of(Map.of(
                    "step", "Input validation",
                    "result", "Amount must be positive and the email must be valid",
                    "status", "fail"))));
            return itemRepo.save(item);
        }

        Map<String, Object> audit;
        try {
            audit = agent.evaluateRequestThroughAgent(buildAgentPayload(requestId, description, amount, email, rules));
        } catch (Exception e) {
            // Fail closed: if the agent is down, a human decides
            audit = new HashMap<>();
            audit.put("approval_status", "NEEDS_REVIEW");
            audit.put("risk_score", 0.5);
            audit.put("audit_reason", "The AI agent was unavailable, so this payment is held for human review.");
            audit.put("flags", List.of("AGENT_UNAVAILABLE"));
            audit.put("steps", List.of(Map.of(
                    "step", "AI agent",
                    "result", "Agent unavailable: " + truncate(String.valueOf(e.getMessage()), 150),
                    "status", "fail")));
        }

        String verdict = String.valueOf(audit.get("approval_status")).toUpperCase();
        if (!Set.of("APPROVED", "NEEDS_REVIEW", "REJECTED").contains(verdict)) verdict = "NEEDS_REVIEW";

        Object risk = audit.get("risk_score");
        item.setApprovalStatus(verdict);
        item.setRiskScore(risk instanceof Number n ? n.doubleValue() : 0.5);
        item.setAuditReason(truncate(str(audit.get("audit_reason"), ""), 1000));
        item.setStepsJson(toJson(audit.get("steps")));
        Object flags = audit.get("flags");
        item.setFlags(flags instanceof List<?> l
                ? l.stream().map(String::valueOf).collect(Collectors.joining(","))
                : "");
        item.setTransactionStatus(switch (verdict) {
            case "REJECTED" -> "REJECTED";
            case "APPROVED" -> "APPROVED";
            default -> "AWAITING_REVIEW";
        });
        return itemRepo.save(item);
    }

    private Map<String, Object> buildAgentPayload(String requestId, String description, BigDecimal amount,
                                                  String email, SpendingRules rules) {
        List<PayoutItem> history = itemRepo.findByReceiverEmailIgnoreCase(email).stream()
                .filter(this::isCommitted).toList();

        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal spent = itemRepo.findAll().stream()
                .filter(this::isCommitted)
                .filter(i -> i.getCreatedAt() != null && !i.getCreatedAt().isBefore(monthStart))
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant tenMinAgo = Instant.now().minusSeconds(600);
        boolean duplicate = history.stream().anyMatch(i ->
                i.getAmount() != null && i.getAmount().compareTo(amount) == 0
                        && i.getCreatedAt() != null && i.getCreatedAt().isAfter(tenMinAgo));

        Double avg = history.isEmpty() ? null : history.stream()
                                                .mapToDouble(i -> i.getAmount() == null ? 0 : i.getAmount().doubleValue())
                                                .average().orElse(0);

        List<String> keywords = Arrays.stream(String.valueOf(rules.getBlockedKeywords()).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("max_per_payout", rules.getMaxPerPayout().doubleValue());
        ctx.put("monthly_budget", rules.getMonthlyBudget().doubleValue());
        ctx.put("new_recipient_limit", rules.getNewRecipientLimit().doubleValue());
        ctx.put("spent_this_month", spent.doubleValue());
        ctx.put("blocked_keywords", keywords);
        ctx.put("is_new_recipient", history.isEmpty());
        ctx.put("recipient_avg_amount", avg);
        ctx.put("duplicate_recent", duplicate);

        Map<String, Object> payload = new HashMap<>();
        payload.put("request_id", requestId);
        payload.put("item_description", description);
        payload.put("amount", amount.doubleValue());
        payload.put("vendor_email", email);
        payload.put("context", ctx);
        return payload;
    }

    // ---------- PayPal ----------

    private void sendToPayPal(List<PayoutItem> items) {
        try {
            List<PayoutLine> lines = items.stream().map(i -> new PayoutLine(
                    i.getSenderItemId(),
                    i.getReceiverEmail(),
                    i.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    i.getDescription() == null || i.getDescription().isBlank()
                            ? "Payout via PayAgent AI" : truncate(i.getDescription(), 200))).toList();

            Map<String, Object> header = paypal.createBatch("PA-" + shortId(), lines);
            String ppBatchId = (String) header.get("payout_batch_id");
            String status = String.valueOf(header.get("batch_status"));

            for (PayoutItem i : items) {
                i.setPaypalBatchId(ppBatchId);
                i.setTransactionStatus(status);
                itemRepo.save(i);
            }
        } catch (HttpStatusCodeException e) {
            markFailed(items, "PayPal error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            markFailed(items, "Payment error: " + e.getMessage());
        }
    }

    private void markFailed(List<PayoutItem> items, String message) {
        for (PayoutItem i : items) {
            i.setTransactionStatus("PAYMENT_FAILED");
            i.setAuditReason(truncate((i.getAuditReason() == null ? "" : i.getAuditReason()) + " | " + message, 1000));
            itemRepo.save(i);
        }
    }

    // ---------- Human review ----------

    public ItemView approve(String itemId) {
        PayoutItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        if (!"AWAITING_REVIEW".equals(item.getTransactionStatus()))
            throw new IllegalStateException("Only items awaiting review can be approved");

        appendStep(item, "Human review", "Approved by a reviewer", "pass");
        item.setTransactionStatus("APPROVED");
        itemRepo.save(item);
        sendToPayPal(List.of(item));
        return toView(item);
    }

    public ItemView reject(String itemId) {
        PayoutItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        if (!"AWAITING_REVIEW".equals(item.getTransactionStatus()))
            throw new IllegalStateException("Only items awaiting review can be rejected");

        appendStep(item, "Human review", "Rejected by a reviewer", "fail");
        item.setTransactionStatus("REVIEW_REJECTED");
        itemRepo.save(item);
        return toView(item);
    }

    // ---------- Reads ----------

    public List<ItemView> ledger() {
        return itemRepo.findAll().stream()
                .sorted(Comparator.comparing(PayoutItem::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toView)
                .toList();
    }

    public Map<String, Object> stats() {
        List<PayoutItem> all = itemRepo.findAll();
        SpendingRules rules = getRules();
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Predicate<PayoutItem> blocked = i -> Set.of("REJECTED", "REVIEW_REJECTED")
                .contains(String.valueOf(i.getTransactionStatus()));
        Predicate<PayoutItem> waiting = i -> "AWAITING_REVIEW".equals(i.getTransactionStatus());
        Predicate<PayoutItem> paid = this::isCommitted;
        Predicate<PayoutItem> paidThisMonth = i -> isCommitted(i)
                && i.getCreatedAt() != null && !i.getCreatedAt().isBefore(monthStart);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRequests", all.size());
        out.put("paidCount", all.stream().filter(paid).count());
        out.put("paidAmount", sum(all, paid));
        out.put("blockedCount", all.stream().filter(blocked).count());
        out.put("blockedAmount", sum(all, blocked));
        out.put("reviewCount", all.stream().filter(waiting).count());
        out.put("reviewAmount", sum(all, waiting));
        out.put("injectionsCaught", all.stream()
                .filter(i -> i.getFlags() != null && i.getFlags().contains("PROMPT_INJECTION")).count());
        out.put("spentThisMonth", sum(all, paidThisMonth));
        out.put("monthlyBudget", rules.getMonthlyBudget());
        return out;
    }

    @SuppressWarnings("unchecked")
    public int refreshStatuses() {
        Map<String, List<PayoutItem>> byBatch = itemRepo.findAll().stream()
                .filter(i -> i.getPaypalBatchId() != null
                        && Set.of("PENDING", "ONHOLD").contains(String.valueOf(i.getTransactionStatus())))
                .collect(Collectors.groupingBy(PayoutItem::getPaypalBatchId));

        int updated = 0;
        for (Map.Entry<String, List<PayoutItem>> e : byBatch.entrySet()) {
            try {
                Map<String, Object> details = paypal.fetchBatch(e.getKey());
                if (!(details.get("items") instanceof List<?> list)) continue;
                for (Object o : list) {
                    Map<String, Object> pi = (Map<String, Object>) o;
                    Map<String, Object> payoutItem = (Map<String, Object>) pi.get("payout_item");
                    if (payoutItem == null) continue;
                    String senderItemId = String.valueOf(payoutItem.get("sender_item_id"));
                    String st = (String) pi.get("transaction_status");
                    for (PayoutItem item : e.getValue()) {
                        if (item.getSenderItemId().equals(senderItemId)
                                && st != null && !st.equals(item.getTransactionStatus())) {
                            item.setTransactionStatus(st);
                            item.setTransactionId((String) pi.get("transaction_id"));
                            itemRepo.save(item);
                            updated++;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Keep going; one failed lookup should not block the rest
            }
        }
        return updated;
    }

    // ---------- Helpers ----------

    private boolean isCommitted(PayoutItem i) {
        return i.getTransactionStatus() != null && !UNPAID.contains(i.getTransactionStatus());
    }

    private BigDecimal sum(List<PayoutItem> items, Predicate<PayoutItem> filter) {
        return items.stream().filter(filter)
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void appendStep(PayoutItem item, String step, String result, String status) {
        List<Map<String, Object>> steps = new ArrayList<>();
        try {
            if (item.getStepsJson() != null) steps = new ArrayList<>(mapper.readValue(item.getStepsJson(), STEP_LIST));
        } catch (Exception ignored) {
        }
        steps.add(Map.of("step", step, "result", result, "status", status));
        item.setStepsJson(toJson(steps));
    }

    private ItemView toView(PayoutItem i) {
        Object steps = List.of();
        try {
            if (i.getStepsJson() != null) steps = mapper.readValue(i.getStepsJson(), Object.class);
        } catch (Exception ignored) {
        }
        List<String> flags = (i.getFlags() == null || i.getFlags().isBlank())
                ? List.of() : Arrays.asList(i.getFlags().split(","));
        return new ItemView(i.getPayoutItemId(),
                i.getPayoutBatch() == null ? null : i.getPayoutBatch().getPayoutBatchId(),
                i.getRequestId(), i.getReceiverEmail(), i.getAmount(), i.getDescription(),
                i.getApprovalStatus(), i.getRiskScore(), i.getAuditReason(), i.getTransactionStatus(),
                flags, steps, i.getPaypalBatchId(), i.getCreatedAt());
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o == null ? List.of() : o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private BigDecimal parseAmount(Object o) {
        try {
            return new BigDecimal(String.valueOf(o)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Object o, String fallback) {
        return o == null || String.valueOf(o).isBlank() ? fallback : String.valueOf(o);
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}