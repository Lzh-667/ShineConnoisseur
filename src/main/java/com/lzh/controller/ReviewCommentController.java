package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.dto.ReviewCommentDTO;
import com.lzh.dto.ReviewDTO;
import com.lzh.service.IReviewCommentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/reviewComments")
public class ReviewCommentController {

    @Resource
    private IReviewCommentService reviewCommentService;
    @PostMapping("/publish/{reviewId}")
    public Result publishReviewComment(
            @RequestBody ReviewCommentDTO reviewCommentDTO,
            @PathVariable("reviewId") Long reviewId
    ){
        return reviewCommentService.publishReviewComment(reviewCommentDTO,reviewId);
    }

    @GetMapping("/list/root/{reviewId}")
    public Result listRootReviewComment(
            @PathVariable("reviewId") Long reviewId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        return reviewCommentService.listRootReviewComment(reviewId,current);
    }

    @GetMapping("/list/children/{rootId}")
    public Result listChildReviewComment(
            @PathVariable("rootId") Long rootId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        return reviewCommentService.listChildReviewComment(rootId,current);
    }

    @PostMapping("/like/{reviewCommentId}")
    public Result likeReviewComment(@PathVariable("reviewCommentId") Long reviewCommentId){
        return reviewCommentService.likeReviewComment(reviewCommentId);
    }

    @DeleteMapping("/{reviewCommentId}")
    public Result deleteReview(@PathVariable("reviewCommentId") Long reviewCommentId){
        return reviewCommentService.deleteReviewComment(reviewCommentId);
    }

    @GetMapping("/my")
    public Result myReviewComments(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return reviewCommentService.myReviewComments(current);
    }
}
