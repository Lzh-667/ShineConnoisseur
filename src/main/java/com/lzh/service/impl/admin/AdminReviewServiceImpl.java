package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.po.Review;
import com.lzh.service.IAdminReviewService;
import com.lzh.service.IReviewService;
import com.lzh.utils.SystemConstants;
import com.lzh.vo.AdminReviewVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AdminReviewServiceImpl implements IAdminReviewService {

    @Resource
    private IReviewService reviewService;
    @Override
    public Result listReviews(Long current) {
        //1.查询影评列表
        Page<Review> page = reviewService.query()
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //2.转换为VO
        List<AdminReviewVO> vos = page.getRecords()
                .stream()
                .map(review -> {
                    AdminReviewVO vo = new AdminReviewVO();
                    BeanUtils.copyProperties(review, vo);
                    return vo;
                })
                .toList();
        //3.封装并返回
        PageResult<AdminReviewVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(vos);
        return Result.ok(result);
    }
}
