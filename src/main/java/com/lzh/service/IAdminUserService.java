package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminUserService {
    Result list(Long current);

    Result info(Long userId);

    Result status(Long id);
}
