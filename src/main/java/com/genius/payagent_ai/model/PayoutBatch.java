package com.genius.payagent_ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "payout_batches")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PayoutBatch {

    @Id
    @Column(name = "payout_batch_id", nullable = false, unique = true)
    private String payoutBatchId;

    @Column(name = "batch_status")
    private String batchStatus;

    @Column(name = "currency")
    private String currency;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "time_created")
    private Instant timeCreated;

    @OneToMany(mappedBy = "payoutBatch")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PayoutItem> items;
}