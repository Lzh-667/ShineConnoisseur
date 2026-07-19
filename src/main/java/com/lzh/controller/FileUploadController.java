package com.lzh.controller;

import cn.hutool.core.lang.UUID;
import com.lzh.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/upload")
public class FileUploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String UPLOAD_DIR = "uploads/images/";

    @PostMapping("/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.fail("文件名无效");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.fail("仅支持 jpg、jpeg、png、gif、webp 格式");
        }
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String filename = UUID.fastUUID().toString(true) + "." + extension;
        try {
            File dest = new File(dir, filename);
            file.transferTo(dest);
            log.info("文件上传成功: {}", dest.getAbsolutePath());
            return Result.ok("/" + UPLOAD_DIR + filename);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.fail("上传失败，请重试");
        }
    }
}
