package com.lzh.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.lzh.common.Result;
import com.lzh.service.IFileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Slf4j
@Service
public class FileUploadServiceImpl implements IFileUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String AVATAR_SUB_DIR = "images/avatar/";
    private static final String POSTER_SUB_DIR = "images/poster/";

    @Value("${file.base-path}")
    private String basePath;

    @Override
    public Result uploadAvatar(MultipartFile file) {
        return upload(file, AVATAR_SUB_DIR, "/uploads/images/avatar/");
    }

    @Override
    public Result uploadPoster(MultipartFile file) {
        return upload(file, POSTER_SUB_DIR, "/uploads/images/poster/");
    }

    private Result upload(MultipartFile file, String subDir, String urlPrefix) {
        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename)) {
            return Result.fail("文件名无效");
        }
        String extension = getFileExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.fail("仅支持 jpg、jpeg、png、gif、webp 格式");
        }
        File dir = new File(basePath, subDir);
        if (!dir.exists()) {
            boolean mkdir = dir.mkdirs();
            if (!mkdir) {
                log.error("创建上传目录失败: {}", dir.getAbsolutePath());
                return Result.fail("上传失败");
            }
        }
        String filename = UUID.fastUUID().toString(true) + "." + extension;
        try {
            File dest = new File(dir, filename);
            file.transferTo(dest);
            log.info("文件上传成功: {}", dest.getAbsolutePath());
            return Result.ok(urlPrefix + filename);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.fail("上传失败，请重试");
        }
    }

    private String getFileExtension(String filename) {
        int index = filename.lastIndexOf(".");
        if (index == -1) {
            return null;
        }
        return filename.substring(index + 1).toLowerCase();
    }
}
