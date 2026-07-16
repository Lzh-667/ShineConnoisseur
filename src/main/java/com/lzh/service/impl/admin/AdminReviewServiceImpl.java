package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.po.Review;
import com.lzh.service.IAdminReviewService;
import com.lzh.service.IReviewService;
import com.lzh.utils.AdminHolder;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.vo.AdminReviewVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AdminReviewServiceImpl implements IAdminReviewService {

    @Resource
    private IReviewService reviewService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    @Transactional
    @Override
    public Result updateReviewStatus(Long id) {
        Long adminId = AdminHolder.getAdmin().getId();
        //1.判断影评是否存在
        Review review = reviewService.getById(id);
        if(review==null){
            return Result.fail("影评不存在");
        }
        //2.获取影评当前状态
        Integer status = review.getStatus();
        //3.修改数据
        Integer newStatus =  SystemConstants.REVIEW_STATUS_NORMAL.equals(status)
                ? SystemConstants.REVIEW_STATUS_BAN
                : SystemConstants.REVIEW_STATUS_NORMAL;
        boolean success = reviewService.update()
                .set("status", newStatus)
                .eq("id", id)
                .update();
        if(!success){
            log.info("管理员{}修改影评状态失败,reviewId={}",adminId,id);
            return Result.fail("修改失败");
        }
        if(SystemConstants.REVIEW_STATUS_NORMAL.equals(status)){
            log.info("管理员{}禁用了影评{}",adminId,id);
            //删除缓存
            stringRedisTemplate.opsForZSet().remove(RedisConstants.HOT_REVIEW_KEY,id.toString());
            stringRedisTemplate.delete(RedisConstants.LIKE_REVIEW_KEY + id);
        }
        else{
            log.info("管理员{}解封了影评{}",adminId,id);
        }
        return Result.ok();
    }
}
