package com.genius.payagent_ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "spending_rules")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SpendingRules {

    @Id
    private Long id;

    @Column(name = "max_per_payout")
    private BigDecimal maxPerPayout;

    @Column(name = "monthly_budget")
    private BigDecimal monthlyBudget;

    @Column(name = "new_recipient_limit")
    private BigDecimal newRecipientLimit;

    @Column(name = "blocked_keywords", length = 1000)
    private String blockedKeywords;
}