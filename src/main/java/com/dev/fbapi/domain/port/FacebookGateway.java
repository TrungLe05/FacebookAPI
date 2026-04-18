package com.dev.fbapi.domain.port;

import com.dev.fbapi.domain.model.*;

import java.util.List;

public interface FacebookGateway {
    PageInfo getPageInfo(String pageId);
    List<Post> getPagePosts(String pageId);
    String createPost(String pageId, String message, String link);
    boolean deletePost(String postId);
    List<Insight> getInsights(String pageId);
    List<Comment> getPostComments(String postId);
    LikeSummary getPostLikes(String postId);
}
