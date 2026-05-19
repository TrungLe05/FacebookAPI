package com.dev.backendapi.application.usecase;

import com.dev.backendapi.domain.model.LikeSummary;
import com.dev.backendapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPostLikesUseCase {
    private final FacebookGateway facebookGateway;

    public LikeSummary execute(String postId) {
        return facebookGateway.getPostLikes(postId);
    }
}
