package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.po.ReviewComment;
import com.lzh.service.IAdminCommentService;
import com.lzh.service.IReviewCommentService;
import com.lzh.service.IReviewService;
import com.lzh.utils.AdminHolder;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.vo.AdminCommentVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AdminCommentServiceImpl implements IAdminCommentService {

    @Resource
    private IReviewCommentService reviewCommentService;
    @Resource
    private IReviewService reviewService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ReviewCommentMapper reviewCommentMapper;
    @Override
    public Result listComments(Long current) {
        // 使用自定义Mapper方法绕过@TableLogic，查询status=1和status=2的评论
        IPage<ReviewComment> page = reviewCommentMapper
                .selectAdminPage(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<AdminCommentVO> vos = page.getRecords()
                .stream()
                .map(comment -> {
                    AdminCommentVO vo = new AdminCommentVO();
                    BeanUtils.copyProperties(comment, vo);
                    return vo;
                })
                .toList();
        return Result.ok(new PageResult<>(page.getTotal(),vos));
    }

    @Transactional
    @Override
    public Result updateCommentStatus(Long id) {
        Long adminId = AdminHolder.getAdmin().getId();
        ReviewComment comment = reviewCommentService.getById(id);
        if(comment==null){
            return Result.fail("评论不存在");
        }
        Integer status = comment.getStatus();
        Integer newStatus = SystemConstants.COMMENT_STATUS_NORMAL.equals(status)
                ? SystemConstants.COMMENT_STATUS_BAN
                : SystemConstants.COMMENT_STATUS_NORMAL;
        boolean success = reviewCommentService.update()
                .set("status", newStatus)
                .eq("id", id)
                .update();
        if(!success){
            log.info("管理员{}修改评论状态失败,commentId={}",adminId,id);
            return Result.fail("修改失败");
        }
        if(SystemConstants.COMMENT_STATUS_NORMAL.equals(status)){
            // 封禁：扣除影评的评论数
            success = reviewService.update()
                    .setSql("comment_count = comment_count - 1")
                    .gt("comment_count", 0)
                    .eq("id", comment.getReviewId())
                    .update();
            if (!success) {
                throw new RuntimeException("封禁评论：更新影评评论数失败");
            }
            stringRedisTemplate.delete(RedisConstants.LIKE_COMMENT_KEY + id);
            log.info("管理员{}封禁了评论{}",adminId,id);
        }
        else{
            // 解封：恢复影评的评论数
            success = reviewService.update()
                    .setSql("comment_count = comment_count + 1")
                    .eq("id", comment.getReviewId())
                    .update();
            if (!success) {
                throw new RuntimeException("解封评论：更新影评评论数失败");
            }
            log.info("管理员{}解封了评论{}",adminId,id);
        }
        return Result.ok();
    }
}
