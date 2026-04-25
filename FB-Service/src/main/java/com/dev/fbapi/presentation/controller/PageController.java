package com.dev.fbapi.presentation.controller;

import com.dev.fbapi.application.usecase.*;
import com.dev.fbapi.presentation.dto.request.CreatePostRequest;
import com.dev.fbapi.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Page API", description = "Endpoints cho Page Graph API (/{page-id}/...)")
public class PageController {

    private final GetPageInfoUseCase getPageInfoUseCase;
    private final GetPagePostsUseCase getPagePostsUseCase;
    private final CreatePostUseCase createPostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final GetInsightsUseCase getInsightsUseCase;
    private final GetPostCommentsUseCase getPostCommentsUseCase;
    private final GetPostLikesUseCase getPostLikesUseCase;

    @GetMapping("/page/{pageId}")
    @Operation(summary = "Lấy thông tin Page")
    public ApiResponse<?> getPageInfo(@PathVariable String pageId) {
        return ApiResponse.builder()
                .result(getPageInfoUseCase.execute(pageId))
                .build();
    }

    @GetMapping("/page/{pageId}/posts")
    @Operation(summary = "Lấy danh sách bài đăng")
    public ApiResponse<?> getPagePosts(@PathVariable String pageId) {
        return ApiResponse.builder()
                .result(getPagePostsUseCase.execute(pageId))
                .build();
    }

    @PostMapping("/page/{pageId}/posts")
    @Operation(summary = "Tạo bài đăng mới")
    public ApiResponse<?> createPost(
            @PathVariable String pageId,
            @Valid @RequestBody CreatePostRequest req) {
        return ApiResponse.builder()
                .code(201)
                .result(createPostUseCase.execute(pageId, req.getMessage(), req.getLink()))
                .build();
    }

    @DeleteMapping("/page/post/{postId}")
    @Operation(summary = "Xóa bài đăng")
    public ApiResponse<?> deletePost(@PathVariable String postId) {
        return ApiResponse.builder()
                .result(deletePostUseCase.execute(postId))
                .build();
    }

    @GetMapping("/page/{pageId}/insights")
    @Operation(summary = "Lấy Insights của Page")
    public ApiResponse<?> getInsights(@PathVariable String pageId) {
        return ApiResponse.builder()
                .result(getInsightsUseCase.execute(pageId))
                .build();
    }

    @GetMapping("/page/post/{postId}/comments")
    @Operation(summary = "Lấy comments của bài đăng")
    public ApiResponse<?> getComments(@PathVariable String postId) {
        return ApiResponse.builder()
                .result(getPostCommentsUseCase.execute(postId))
                .build();
    }

    @GetMapping("/page/post/{postId}/likes")
    @Operation(summary = "Lấy likes của bài đăng")
    public ApiResponse<?> getLikes(@PathVariable String postId) {
        return ApiResponse.builder()
                .result(getPostLikesUseCase.execute(postId))
                .build();
    }
}
