package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminUserService {
    Result list(Integer current);

    Result updateStatus(Long id);
}
