package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@Slf4j
@RestController
@RequestMapping("/admins/reviews")
@Tag(name = "管理端-影评", description = "影评列表、封禁/解封")
public class AdminReviewController {

    @Resource
    private IAdminReviewService adminReviewService;
    @Operation(summary = "影评列表")
    @GetMapping("/list")
    public Result listReviews(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return adminReviewService.listReviews(current);
    }
    @Operation(summary = "封禁/解封影评")
    @PutMapping("/status/{id}")
    public Result updateReviewStatus(@PathVariable Long id){
        return adminReviewService.updateReviewStatus(id);
    }

}
