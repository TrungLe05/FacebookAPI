package com.dev.backendapi.application.usecase;

import com.dev.backendapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SendMessageUseCase {
    private final FacebookGateway facebookGateway;

    public void execute(String recipientId, String message) {
        facebookGateway.sendMessage(recipientId, message);
    }
}
