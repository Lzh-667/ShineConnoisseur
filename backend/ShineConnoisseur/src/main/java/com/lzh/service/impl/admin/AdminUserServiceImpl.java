package com.lzh.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.utils.AdminHolder;
import com.lzh.utils.RedisConstants;
import com.lzh.vo.AdminUserVO;
import com.lzh.po.User;
import com.lzh.service.IAdminUserService;
import com.lzh.service.IUserService;
import com.lzh.utils.SystemConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AdminUserServiceImpl implements IAdminUserService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result list(Integer current) {
        //1.查询用户列表
        Page<User> page = userService.query()
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //2.包装为VO
        List<AdminUserVO> vo = page.getRecords()
                .stream()
                .map(user-> BeanUtil.copyProperties(user, AdminUserVO.class))
                .toList();
        //3.封装并返回
        PageResult<AdminUserVO> result = new PageResult<>();
        result.setRecords(vo);
        result.setTotal(page.getTotal());
        return Result.ok(result);
    }
    @Override
    public Result updateStatus(Long id) {
        Long adminId = AdminHolder.getAdmin().getId();
        //1.判断用户是否存在
        User user = userService.getById(id);
        if(user==null){
            return Result.fail("用户不存在");
        }
        //2.获取用户当前状态
        Integer status = user.getStatus();
        //3.修改数据
        Integer newStatus =  SystemConstants.USER_STATUS_NORMAL.equals(status)
                ? SystemConstants.USER_STATUS_BAN
                : SystemConstants.USER_STATUS_NORMAL;
        boolean success = userService.update()
                .set("status", newStatus)
                .eq("id", id)
                .update();
        if(!success){
            log.info("管理员{}修改用户状态失败,userId={}",adminId,id);
            return Result.fail("修改失败");
        }
        if(SystemConstants.USER_STATUS_NORMAL.equals(status)){
            // 清除被封禁用户的Redis登录态
            String token = stringRedisTemplate.opsForValue().get(RedisConstants.USER_TOKEN_KEY + id);
            if (token != null) {
                stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
                stringRedisTemplate.delete(RedisConstants.USER_TOKEN_KEY + id);
                stringRedisTemplate.delete(RedisConstants.USER_FAVORITE_MOVIE_KEY + id);
                stringRedisTemplate.delete(RedisConstants.FOLLOWER_KEY + id);
                stringRedisTemplate.delete(RedisConstants.FOLLOWING_KEY + id);
            }
            log.info("管理员{}禁用了用户{}",adminId,id);
        }
        else{
            log.info("管理员{}解封了用户{}",adminId,id);
        }
        return Result.ok();
    }
}
