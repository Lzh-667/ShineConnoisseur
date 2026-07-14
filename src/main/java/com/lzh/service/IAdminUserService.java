package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminUserService {
    Result list(Long current);

    Result updateStatus(Long id);
}
