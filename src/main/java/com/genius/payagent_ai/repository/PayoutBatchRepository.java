package com.genius.payagent_ai.repository;

import com.genius.payagent_ai.model.PayoutBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutBatchRepository extends JpaRepository<PayoutBatch,String> {
}