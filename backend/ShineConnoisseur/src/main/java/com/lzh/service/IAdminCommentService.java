package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminCommentService {
    Result listComments(Integer current);

    Result updateCommentStatus(Long id);
}
