package com.genius.payagent_ai.controller;

import com.genius.payagent_ai.service.PayPalAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


@RestController
@RequestMapping("/api/paypal")
public class PayPalController {
    @Autowired
    private PayPalAuthService payPalAuthService;

    @Value("${paypal.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/payout")
    public ResponseEntity executePayout(@RequestBody Map<String, Object> payoutRequest){
        String accessToken = payPalAuthService.getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization","Bearer "+accessToken);

        HttpEntity entity = new HttpEntity<>(payoutRequest,headers);

        ResponseEntity response = restTemplate.postForEntity(
                baseUrl + "/v1/payments/payouts", entity, Map.class
        );

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}