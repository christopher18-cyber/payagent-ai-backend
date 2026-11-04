package com.genius.payagent_ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
        String auth = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization","Basic "+ encodedAuth);

        HttpEntity<String> request =new HttpEntity<>("grant_type=client_credentials",headers);

        ResponseEntity response = restTemplate.postForEntity(
                baseUrl + "/v1/oauth2/token", request, Map.class
        );

        if(response.getStatusCode().is2xxSuccessful() && response.getBody() != null){
            Map<?,?> body = (Map<?, ?>) response.getBody();
            return (String) body.get("access_token");
        }

        throw new RuntimeException("Failed to obtain Paypal Access Token");
    }
}
