package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminCommentService {
    Result listComments(Long current);

    Result updateCommentStatus(Long id);
}
