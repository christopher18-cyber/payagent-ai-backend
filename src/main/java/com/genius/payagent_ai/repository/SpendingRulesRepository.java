package com.genius.payagent_ai.repository;

import com.genius.payagent_ai.model.SpendingRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpendingRulesRepository extends JpaRepository<SpendingRules, Long> {
}