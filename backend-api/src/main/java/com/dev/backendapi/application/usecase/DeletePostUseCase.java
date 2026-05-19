package com.dev.backendapi.application.usecase;

import com.dev.backendapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeletePostUseCase {
    private final FacebookGateway facebookGateway;

    public boolean execute(String postId) {
        return facebookGateway.deletePost(postId);
    }
}
