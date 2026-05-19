package com.dev.backendapi.application.usecase;

import com.dev.backendapi.domain.model.Comment;
import com.dev.backendapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPostCommentsUseCase {
    private final FacebookGateway facebookGateway;

    public List<Comment> execute(String postId) {
        return facebookGateway.getPostComments(postId);
    }
}
