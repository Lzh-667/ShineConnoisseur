package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.mapper.ReviewMapper;
import com.lzh.po.Review;
import com.lzh.service.IAdminReviewService;
import com.lzh.service.IMovieService;
import com.lzh.service.IReviewService;
import com.lzh.service.IUserService;
import com.lzh.utils.AdminHolder;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.vo.AdminReviewVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AdminReviewServiceImpl implements IAdminReviewService {

    @Resource
    private IReviewService reviewService;
    @Resource
    private IMovieService movieService;
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ReviewMapper reviewMapper;

    @Override
    public Result listReviews(Long current) {
        // 使用自定义Mapper方法绕过@TableLogic，查询status=1和status=2的影评
        IPage<Review> page = reviewMapper
                .selectAdminPage(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<AdminReviewVO> vos = page.getRecords()
                .stream()
                .map(review -> {
                    AdminReviewVO vo = new AdminReviewVO();
                    BeanUtils.copyProperties(review, vo);
                    return vo;
                })
                .toList();
        return Result.ok(new PageResult<>(page.getTotal(),vos));
    }

    @Transactional
    @Override
    public Result updateReviewStatus(Long id) {
        Long adminId = AdminHolder.getAdmin().getId();
        Review review = reviewService.getById(id);
        if(review==null){
            return Result.fail("影评不存在");
        }
        Integer status = review.getStatus();
        Integer newStatus = SystemConstants.REVIEW_STATUS_NORMAL.equals(status)
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
            // 封禁：扣除电影评分和用户影评数
            success = movieService.update()
                    .setSql("rating_sum = rating_sum - " + review.getRating())
                    .setSql("rating_count = rating_count - 1")
                    .gt("rating_count", 0)
                    .eq("id", review.getMovieId())
                    .update();
            if (!success) {
                throw new RuntimeException("封禁影评：更新电影评分失败");
            }
            success = userService.update()
                    .setSql("review_count = review_count - 1")
                    .gt("review_count", 0)
                    .eq("id", review.getUserId())
                    .update();
            if (!success) {
                throw new RuntimeException("封禁影评：更新用户影评数失败");
            }
            // 删除缓存
            stringRedisTemplate.opsForZSet().remove(RedisConstants.HOT_REVIEW_KEY,id.toString());
            stringRedisTemplate.delete(RedisConstants.LIKE_REVIEW_KEY + id);
            log.info("管理员{}封禁了影评{}",adminId,id);
        }
        else{
            // 解封：恢复电影评分和用户影评数
            success = movieService.update()
                    .setSql("rating_sum = rating_sum + " + review.getRating())
                    .setSql("rating_count = rating_count + 1")
                    .eq("id", review.getMovieId())
                    .update();
            if (!success) {
                throw new RuntimeException("解封影评：更新电影评分失败");
            }
            success = userService.update()
                    .setSql("review_count = review_count + 1")
                    .eq("id", review.getUserId())
                    .update();
            if (!success) {
                throw new RuntimeException("解封影评：更新用户影评数失败");
            }
            log.info("管理员{}解封了影评{}",adminId,id);
        }
        return Result.ok();
    }
}
