package com.genius.payagent_ai.repository;

import com.genius.payagent_ai.model.PayoutItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutItemRepository extends JpaRepository<PayoutItem, String> {
    List<PayoutItem> findByReceiverEmailIgnoreCase(String receiverEmail);
}