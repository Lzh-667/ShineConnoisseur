package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IFileUploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/admins/upload")
@Tag(name = "管理端-上传", description = "电影海报上传")
public class AdminFileUploadController {

    @Resource
    private IFileUploadService fileUploadService;

    @Operation(summary = "上传海报")
    @PostMapping("/poster")
    public Result uploadPoster(@RequestParam("file") MultipartFile file) {
        return fileUploadService.uploadPoster(file);
    }
}
