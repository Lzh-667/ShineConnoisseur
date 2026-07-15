package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/admins/reviews")
public class AdminReviewController {

    @Resource
    private IAdminReviewService adminReviewService;
    @GetMapping("/list")
    public Result listReviews(@RequestParam(value = "current", defaultValue = "1") Long current){
        return adminReviewService.listReviews(current);
    }
    @PutMapping("/status/{id}")
    public Result updateReviewStatus(@PathVariable Long id){
        return adminReviewService.updateReviewStatus(id);
    }

}
