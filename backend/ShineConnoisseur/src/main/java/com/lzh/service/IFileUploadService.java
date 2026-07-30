package com.lzh.service;

import com.lzh.common.Result;
import org.springframework.web.multipart.MultipartFile;

public interface IFileUploadService {
    Result uploadAvatar(MultipartFile file);
    Result uploadPoster(MultipartFile file);
}
