package com.dev.backendapi.domain.port;

import com.dev.backendapi.domain.model.*;

import java.util.List;

public interface FacebookGateway {
    PageInfo getPageInfo(String pageId);
    List<Post> getPagePosts(String pageId);
    String createPost(String pageId, String message, String link);
    boolean deletePost(String postId);
    List<Insight> getInsights(String pageId);
    List<Comment> getPostComments(String postId);
    LikeSummary getPostLikes(String postId);

    boolean hideComment(String commentId);
    String replyToComment(String commentId, String message);
    void sendMessage(String recipientId, String message);
    boolean deleteComment(String commentId);
}
