package com.dev.backendapi.presentation.controller;

import com.dev.backendapi.application.usecase.*;
import com.dev.backendapi.presentation.dto.request.CreatePostRequest;
import com.dev.backendapi.presentation.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PageController {

    private final GetPageInfoUseCase getPageInfoUseCase;
    private final GetPagePostsUseCase getPagePostsUseCase;
    private final CreatePostUseCase createPostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final GetInsightsUseCase getInsightsUseCase;
    private final GetPostCommentsUseCase getPostCommentsUseCase;
    private final GetPostLikesUseCase getPostLikesUseCase;

    @GetMapping("/page/{pageId}")
    public ApiResponse<?> getPageInfo(@PathVariable String pageId) {
        return ApiResponse.builder()
                .result(getPageInfoUseCase.execute(pageId))
                .build();
    }

    @GetMapping("/page/{pageId}/posts")
    public ApiResponse<?> getPagePosts(@PathVariable String pageId) {
        return ApiResponse.builder()
                .result(getPagePostsUseCase.execute(pageId))
                .build();
    }

    @PostMapping("/page/{pageId}/posts")
    public ApiResponse<?> createPost(
            @PathVariable String pageId,
            @Valid @RequestBody CreatePostRequest req) {
        return ApiResponse.builder()
                .code(201)
                .result(createPostUseCase.execute(pageId, req.getMessage(), req.getLink()))
                .build();
    }

    @DeleteMapping("/page/post/{postId}")
    public ApiResponse<?> deletePost(@PathVariable String postId) {
        return ApiResponse.builder()
                .result(deletePostUseCase.execute(postId))
                .build();
    }

    @GetMapping("/page/{pageId}/insights")
    public ApiResponse<?> getInsights(@PathVariable String pageId) {
        return ApiResponse.builder()
                .result(getInsightsUseCase.execute(pageId))
                .build();
    }

    @GetMapping("/page/post/{postId}/comments")
    public ApiResponse<?> getComments(@PathVariable String postId) {
        return ApiResponse.builder()
                .result(getPostCommentsUseCase.execute(postId))
                .build();
    }

    @GetMapping("/page/post/{postId}/likes")
    public ApiResponse<?> getLikes(@PathVariable String postId) {
        return ApiResponse.builder()
                .result(getPostLikesUseCase.execute(postId))
                .build();
    }
}
