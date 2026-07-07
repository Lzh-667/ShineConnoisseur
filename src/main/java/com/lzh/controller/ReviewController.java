package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.dto.ReviewDTO;
import com.lzh.service.IReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/reviews")
public class ReviewController {

    @Resource
    private IReviewService reviewService;

    @PostMapping("/publish/{movieId}")
    public Result publishReview(
            @RequestBody ReviewDTO reviewDTO,
            @PathVariable("movieId") Long movieId
    ){
        return reviewService.publishReview(reviewDTO,movieId);
    }
    @GetMapping("/movie/{movieId}")
    public Result listReview(
            @PathVariable("movieId") Long movieId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        return reviewService.listReview(movieId,current);
    }
    @GetMapping("/my")
    public Result myReviews(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return reviewService.myReviews(current);
    }

    @GetMapping("/or/not/{reviewId}")
    public Result isLike(@PathVariable("reviewId") Long reviewId){
        return reviewService.isLike(reviewId);
    }

    @PostMapping("/like/{reviewId}/{isLike}")
    public Result likeReview(
            @PathVariable("reviewId") Long reviewId,
            @PathVariable("isLike") Boolean isLike
    ){
        return reviewService.likeReview(reviewId,isLike);
    }

    @PutMapping("/{reviewId}")
    public Result updateReview(
            @PathVariable("reviewId") Long reviewId,
            @RequestBody ReviewDTO reviewDTO
    ){
        return reviewService.updateReview(reviewId,reviewDTO);
    }

    //TODO 未测试
    @DeleteMapping("/{reviewId}")
    public Result deleteReview(@PathVariable("reviewId") Long reviewId){
        return reviewService.deleteReview(reviewId);
    }
}
