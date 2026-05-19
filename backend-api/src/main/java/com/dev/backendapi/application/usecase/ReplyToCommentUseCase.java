package com.dev.backendapi.application.usecase;

import com.dev.backendapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplyToCommentUseCase {
    private final FacebookGateway facebookGateway;

    public String execute(String commentId, String message) {
        return facebookGateway.replyToComment(commentId, message);
    }
}
