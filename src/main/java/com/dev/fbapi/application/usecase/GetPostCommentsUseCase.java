package com.dev.fbapi.application.usecase;

import com.dev.fbapi.domain.model.Comment;
import com.dev.fbapi.domain.port.FacebookGateway;
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
