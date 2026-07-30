package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.dto.ReviewCommentDTO;
import com.lzh.service.IReviewCommentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Slf4j
@RequestMapping("/reviewComments")
@Tag(name = "评论模块", description = "影评评论发布、回复、点赞、删除")
public class ReviewCommentController {

    @Resource
    private IReviewCommentService reviewCommentService;
    @Operation(summary = "发布评论")
    @PostMapping("/publish/{reviewId}")
    public Result publishReviewComment(
            @RequestBody ReviewCommentDTO reviewCommentDTO,
            @PathVariable("reviewId") Long reviewId
    ){
        return reviewCommentService.publishReviewComment(reviewCommentDTO,reviewId);
    }

    @Operation(summary = "根评论列表")
    @GetMapping("/list/root/{reviewId}")
    public Result listRootReviewComment(
            @PathVariable("reviewId") Long reviewId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        return reviewCommentService.listRootReviewComment(reviewId,current);
    }

    @Operation(summary = "子回复列表")
    @GetMapping("/list/children/{rootId}")
    public Result listChildReviewComment(
            @PathVariable("rootId") Long rootId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        return reviewCommentService.listChildReviewComment(rootId,current);
    }

    @Operation(summary = "点赞/取消点赞评论")
    @PostMapping("/like/{reviewCommentId}")
    public Result likeReviewComment(@PathVariable("reviewCommentId") Long reviewCommentId){
        return reviewCommentService.likeReviewComment(reviewCommentId);
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{reviewCommentId}")
    public Result deleteReview(@PathVariable("reviewCommentId") Long reviewCommentId){
        return reviewCommentService.deleteReviewComment(reviewCommentId);
    }

    @Operation(summary = "我的评论")
    @GetMapping("/my")
    public Result myReviewComments(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return reviewCommentService.myReviewComments(current);
    }

    @Operation(summary = "获取评论所属影评")
    @GetMapping("/{id}/target")
    public Result getTargetReview(@PathVariable("id") Long id){
        return reviewCommentService.getTargetReview(id);
    }
}
