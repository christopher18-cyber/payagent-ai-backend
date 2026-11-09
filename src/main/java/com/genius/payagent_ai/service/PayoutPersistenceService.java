package com.genius.payagent_ai.service;

import com.genius.payagent_ai.model.PayoutBatch;
import com.genius.payagent_ai.model.PayoutItem;
import com.genius.payagent_ai.repository.PayoutBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PayoutPersistenceService {
    private final PayoutBatchRepository batchRepository;

    public PayoutPersistenceService(PayoutBatchRepository batchRepository){
        this.batchRepository = batchRepository;
    }

    @Transactional
    public PayoutBatch savePayoutResponse(Map<String,Object> paypalResponse){
        Map<String, Object> batchHeader = (Map<String, Object>) paypalResponse.get("batch_header");
        Map<String, Object> amountMap = (Map<String, Object>) batchHeader.get("amount");

        String batchId = (String) batchHeader.get("payout_batch_id");
        String status = (String) batchHeader.get("batch_status");
        String currency = (String) amountMap.get("currency");
        BigDecimal totalAmount = new BigDecimal((String) amountMap.get("value"));
        Instant timeCreated = Instant.parse((String) batchHeader.get("time_created"));

        PayoutBatch batch = PayoutBatch.builder()
                .payoutBatchId(batchId)
                .batchStatus(status)
                .currency(currency)
                .totalAmount(totalAmount)
                .timeCreated(timeCreated)
                .build();

        List itemsList =(List) paypalResponse.get("items");

        if(itemsList != null){
            List<PayoutItem> items = (List<PayoutItem>) itemsList.stream().map(rawItem -> {
                Map<String,Object> itemData = (Map<String, Object>) rawItem;
                String payoutItemId = (String) itemData.get("payout_item_id");
                String txStatus = (String) itemData.get("transaction_status");
                String txId = (String) itemData.get("transaction_id");

                Map<String, Object> payoutItemDetails = (Map<String, Object>) itemData.get("payout_item");
                String receiver = (String) payoutItemDetails.get("receiver");

                Map<String,Object> itemAmountMap = (Map<String, Object>) itemData.get("amount");
                BigDecimal itemAmount = new BigDecimal((String) itemAmountMap.get("value"));

                Map<String, Object> feeMap = (Map<String, Object>) itemData.get("payout_item_out");
                BigDecimal feeAmount = feeMap != null ? new BigDecimal((String) feeMap.get("value")) : BigDecimal.ZERO;

                String senderItemId = (String) payoutItemDetails.get("sender_item_id");

                return PayoutItem.builder()
                        .payoutItemId(payoutItemId)
                        .payoutBatch(batch)
                        .transactionStatus(txStatus)
                        .transactionId(txId)
                        .receiverEmail(receiver)
                        .amount(itemAmount)
                        .feeAmount(feeAmount)
                        .senderItemId(senderItemId)
                        .build();
            }).collect(Collectors.toList());
            batch.setItems(items);
        }

        return batchRepository.save(batch);
    }
}