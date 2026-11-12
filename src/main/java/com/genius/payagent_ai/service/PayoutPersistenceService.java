package com.genius.payagent_ai.service;

import com.genius.payagent_ai.model.PayoutBatch;
import com.genius.payagent_ai.model.PayoutItem;
import com.genius.payagent_ai.repository.PayoutBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PayoutPersistenceService {

    private final PayoutBatchRepository batchRepository;

    public PayoutPersistenceService(PayoutBatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    /**
     * PayPal's create-payout response only returns the batch ID and status,
     * so the rest is saved from our own request data plus the agent's audit result.
     */
    @Transactional
    public PayoutBatch saveCreated(String batchId, String status, BigDecimal amount,
                                   String receiver, String description, String senderItemId,
                                   Map<String, Object> audit) {

        PayoutBatch batch = PayoutBatch.builder()
                .payoutBatchId(batchId)
                .batchStatus(status)
                .currency("USD")
                .totalAmount(amount)
                .timeCreated(Instant.now())
                .build();

        Object risk = audit.get("risk_score");

        PayoutItem item = PayoutItem.builder()
                .payoutItemId(senderItemId)
                .payoutBatch(batch)
                .transactionStatus(status)
                .receiverEmail(receiver)
                .amount(amount)
                .feeAmount(BigDecimal.ZERO)
                .senderItemId(senderItemId)
                .description(description)
                .riskScore(risk instanceof Number n ? n.doubleValue() : null)
                .approvalStatus(String.valueOf(audit.get("approval_status")))
                .auditReason(String.valueOf(audit.get("audit_reason")))
                .build();

        batch.setItems(new ArrayList<>(List.of(item)));

        return batchRepository.save(batch);
    }
}