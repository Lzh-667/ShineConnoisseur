package com.lzh.controller;

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
@RequestMapping("/upload")
@Tag(name = "文件上传", description = "用户头像上传")
public class FileUploadController {

    @Resource
    private IFileUploadService fileUploadService;

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file) {
        return fileUploadService.uploadAvatar(file);
    }
}
