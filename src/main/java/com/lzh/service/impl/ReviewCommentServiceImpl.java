package com.lzh.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.po.ReviewComment;
import com.lzh.service.IReviewCommentService;
import org.springframework.stereotype.Service;

@Service
public class ReviewCommentServiceImpl extends ServiceImpl<ReviewCommentMapper, ReviewComment> implements IReviewCommentService {
}
