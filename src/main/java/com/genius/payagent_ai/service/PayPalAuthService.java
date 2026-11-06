package com.genius.payagent_ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class PayPalAuthService {

    @Value("${paypal.base-url}")
    private String baseUrl;

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getAccessToken(){

        String cleanClientId = clientId != null ? clientId.trim() : "";
        String cleanClientSecret = clientSecret != null ? clientSecret.trim() : "";

        String auth = cleanClientId + ":" + cleanClientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization","Basic "+ encodedAuth);

        org.springframework.util.MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity response = restTemplate.postForEntity(
                baseUrl + "/v1/oauth2/token", request, Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map responseBody = (Map) response.getBody(); // Cast or assign to a raw/parameterized Map variable
            return (String) responseBody.get("access_token");
        }
        throw new RuntimeException("Failed to obtain Paypal Access Token");
    }


    public Map getPayoutStatus(String batchId, String accessToken){
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization","Bearer "+accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(headers);

        ResponseEntity response = restTemplate.exchange(
                baseUrl + "/v1/payments/payouts/" + batchId,
                HttpMethod.GET,
                request,
                Map.class
        );

        if(response.getStatusCode().is2xxSuccessful() && response.getBody() != null){
            return (Map) response.getBody();
        }

        throw new RuntimeException("Failed to fetch payout status from PayPal");
    }
}