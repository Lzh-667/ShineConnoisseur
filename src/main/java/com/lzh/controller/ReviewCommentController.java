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
    @PostMapping("/publish/{review}")
    public Result publishReviewComment(
            @RequestBody ReviewCommentDTO reviewCommentDTO,
            @PathVariable("review") Long reviewId
    ){
        return reviewCommentService.publishReviewComment(reviewCommentDTO,reviewId);
    }
}
