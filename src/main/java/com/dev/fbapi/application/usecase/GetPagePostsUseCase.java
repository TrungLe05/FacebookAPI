package com.dev.fbapi.application.usecase;

import com.dev.fbapi.domain.model.Post;
import com.dev.fbapi.domain.port.FacebookGateway;
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
