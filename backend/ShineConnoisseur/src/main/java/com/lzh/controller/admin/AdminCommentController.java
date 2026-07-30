package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminCommentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/admins/comments")
@Tag(name = "管理端-评论", description = "评论列表、封禁/解封")
public class AdminCommentController {

    @Resource
    private IAdminCommentService adminCommentService;
    @Operation(summary = "评论列表")
    @GetMapping("/list")
    public Result listComments(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return adminCommentService.listComments(current);
    }
    @Operation(summary = "封禁/解封评论")
    @PutMapping("/status/{id}")
    public Result updateCommentStatus(@PathVariable Long id){
        return adminCommentService.updateCommentStatus(id);
    }
}
