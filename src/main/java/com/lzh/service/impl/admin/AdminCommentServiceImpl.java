package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.po.ReviewComment;
import com.lzh.service.IAdminCommentService;
import com.lzh.service.IReviewCommentService;
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
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result listComments(Long current) {
        //1.查询评论列表
        Page<ReviewComment> page = reviewCommentService.query()
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //2.转换为VO
        List<AdminCommentVO> vos = page.getRecords()
                .stream()
                .map(comment -> {
                    AdminCommentVO vo = new AdminCommentVO();
                    BeanUtils.copyProperties(comment, vo);
                    return vo;
                })
                .toList();
        //3.封装并返回
        PageResult<AdminCommentVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(vos);
        return Result.ok(result);
    }

    @Transactional
    @Override
    public Result updateCommentStatus(Long id) {
        Long adminId = AdminHolder.getAdmin().getId();
        //1.判断评论是否存在
        ReviewComment comment = reviewCommentService.getById(id);
        if(comment==null){
            return Result.fail("评论不存在");
        }
        //2.获取评论当前状态
        Integer status = comment.getStatus();
        //3.修改数据
        Integer newStatus =  SystemConstants.COMMENT_STATUS_NORMAL.equals(status)
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
            log.info("管理员{}禁用了评论{}",adminId,id);
            //删除缓存
            stringRedisTemplate.delete(RedisConstants.LIKE_COMMENT_KEY+id);
        }
        else{
            log.info("管理员{}解封了评论{}",adminId,id);
        }
        return Result.ok();
    }
}
