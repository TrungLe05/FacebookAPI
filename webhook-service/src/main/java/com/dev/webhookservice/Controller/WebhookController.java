package com.dev.webhookservice.Controller;

import com.dev.webhookservice.Service.WebhookService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @Value("${facebook.verify-token}")
    private String verifyToken;

    @GetMapping(produces = "text/plain")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        
        log.info("Received verification request with mode: {}, token: {}, challenge: {}", mode, token, challenge);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook verified successfully!");
            return ResponseEntity.ok(challenge);
        }
        
        log.warn("Webhook verification failed! Expected token: {}, Received token: {}", verifyToken, token);
        return ResponseEntity.status(403).body("Forbidden");
    }

    @PostMapping
    public ResponseEntity<String> receiveWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        log.info("=== Received POST webhook event from Facebook ===");
        try {
            webhookService.processWebhook(rawBody, signature);
            log.info("Webhook event processed successfully");
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("Failed to process webhook event: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error processing event");
        }
    }
}
