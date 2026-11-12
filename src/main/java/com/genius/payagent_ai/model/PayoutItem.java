package com.genius.payagent_ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payout_items")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PayoutItem {

    @Id
    @Column(name = "payout_item_id", nullable = false, unique = true)
    private String payoutItemId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payout_batch_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PayoutBatch payoutBatch;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "receiver_email")
    private String receiverEmail;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Column(name = "sender_item_id")
    private String senderItemId;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "approval_status")
    private String approvalStatus;

    @Column(name = "audit_reason", length = 1000)
    private String auditReason;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "flags", length = 500)
    private String flags;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "paypal_batch_id")
    private String paypalBatchId;

    @Column(name = "created_at")
    private Instant createdAt;
}