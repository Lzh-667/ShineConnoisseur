package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.dto.ReviewDTO;
import com.lzh.service.IReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/reviews")
@Tag(name = "影评模块", description = "影评发布、修改、删除、点赞、热门、搜索")
public class ReviewController {

    @Resource
    private IReviewService reviewService;

    @Operation(summary = "发布影评")
    @PostMapping("/publish/{movieId}")
    public Result publishReview(
            @RequestBody ReviewDTO reviewDTO,
            @PathVariable("movieId") Long movieId
    ){
        return reviewService.publishReview(reviewDTO,movieId);
    }
    @Operation(summary = "电影影评列表")
    @GetMapping("/movie/{movieId}")
    public Result listReview(
            @PathVariable("movieId") Long movieId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        return reviewService.listReview(movieId,current);
    }
    @Operation(summary = "我的影评")
    @GetMapping("/my")
    public Result myReviews(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return reviewService.myReviews(current);
    }

    @Operation(summary = "用户影评列表")
    @GetMapping("/user/{userId}")
    public Result getUserReviews(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        return reviewService.getUserReviews(userId, current);
    }

    @Operation(summary = "点赞/取消点赞影评")
    @PostMapping("/like/{reviewId}")
    public Result likeReview(@PathVariable("reviewId") Long reviewId){
        return reviewService.likeReview(reviewId);
    }

    @Operation(summary = "修改影评")
    @PutMapping("/{reviewId}")
    public Result updateReview(
            @PathVariable("reviewId") Long reviewId,
            @RequestBody ReviewDTO reviewDTO
    ){
        return reviewService.updateReview(reviewId,reviewDTO);
    }

    @Operation(summary = "删除影评")
    @DeleteMapping("/{reviewId}")
    public Result deleteReview(@PathVariable("reviewId") Long reviewId){
        return reviewService.deleteReview(reviewId);
    }

    @Operation(summary = "热门影评")
    @GetMapping("/hot")
    public Result hotReviews(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return reviewService.hotReviews(current);
    }
    @Operation(summary = "影评详情")
    @GetMapping("/{reviewId}")
    public Result getReviewDetail(@PathVariable("reviewId") Long reviewId){
        return reviewService.getReviewDetail(reviewId);
    }

    @Operation(summary = "搜索影评")
    @GetMapping("/search")
    public Result searchReviews(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "spoiler", required = false) Integer spoiler
    ) {
        return reviewService.searchReviews(keyword, current, spoiler);
    }
}
