package com.dev.fbapi.application.usecase;

import com.dev.fbapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreatePostUseCase {
    private final FacebookGateway facebookGateway;

    public String execute(String pageId, String message, String link) {
        return facebookGateway.createPost(pageId, message, link);
    }
}
