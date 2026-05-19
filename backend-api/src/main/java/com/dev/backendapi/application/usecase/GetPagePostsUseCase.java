package com.dev.backendapi.application.usecase;

import com.dev.backendapi.domain.model.Post;
import com.dev.backendapi.domain.port.FacebookGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPagePostsUseCase {
    private final FacebookGateway facebookGateway;

    public List<Post> execute(String pageId) {
        return facebookGateway.getPagePosts(pageId);
    }
}
