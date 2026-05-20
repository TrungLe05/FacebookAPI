package com.dev.backendapi.presentation.controller;

import com.dev.backendapi.infrastructure.config.FacebookProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final FacebookProperties facebookProperties;

    @GetMapping("/status")
    public String getStatus() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("facebookApi");
        return "State: " + cb.getState() +
                " | FailureRate: " + cb.getMetrics().getFailureRate() + "%" +
                " | BufferedCalls: " + cb.getMetrics().getNumberOfBufferedCalls() +
                " | CurrentUrl: " + facebookProperties.getBaseUrl();
    }

    @PostMapping("/reset")
    public String reset() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("facebookApi");
        cb.reset();
        return "Circuit breaker reset → CLOSED. State: " + cb.getState();
    }

    @PostMapping("/open")
    public String forceOpen() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("facebookApi");
        cb.transitionToOpenState();
        return "Circuit breaker forced → OPEN. State: " + cb.getState();
    }

    @PostMapping("/url")
    public String setUrl(@RequestParam String url) {
        facebookProperties.getGraph().getApi().setUrl(url);
        return "URL updated → " + facebookProperties.getBaseUrl();
    }
}
