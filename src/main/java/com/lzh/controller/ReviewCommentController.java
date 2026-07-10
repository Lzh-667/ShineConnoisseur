package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.dto.ReviewCommentDTO;
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

//    @GetMapping("/list/{rootId}")
//    public Result listReviewComment(
//            @PathVariable("rootId") Long rootId,
//            @RequestParam(value = "current", defaultValue = "1") Integer current
//    ){
//        return reviewCommentService.listReviewComment(rootId,current);
//    }
}
