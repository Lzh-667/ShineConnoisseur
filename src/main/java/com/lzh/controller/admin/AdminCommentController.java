package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminCommentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admins/comments")
public class AdminCommentController {

    @Resource
    private IAdminCommentService adminCommentService;
    @GetMapping("/list")
    public Result listComments(@RequestParam(value = "current", defaultValue = "1") Long current){
        return adminCommentService.listComments(current);
    }
    @PutMapping("/status/{id}")
    public Result updateCommentStatus(@PathVariable Long id){
        return adminCommentService.updateCommentStatus(id);
    }
}
