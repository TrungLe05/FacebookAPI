package com.dev.fbapi.application.usecase;

import com.dev.fbapi.domain.model.LikeSummary;
import com.dev.fbapi.domain.port.FacebookGateway;
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
