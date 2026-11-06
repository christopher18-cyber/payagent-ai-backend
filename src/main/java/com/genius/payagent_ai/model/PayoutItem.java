package com.genius.payagent_ai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_batch_id", nullable = false)
    private PayoutBatch payoutBatch;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "receiver_email")
    private String receiverEmail;

    @Column(name="amount")
    private BigDecimal amount;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Column(name = "sender_item_id")
    private String senderItemId;
}