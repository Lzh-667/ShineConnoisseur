package com.lzh.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.vo.AdminUserVO;
import com.lzh.po.User;
import com.lzh.service.IAdminUserService;
import com.lzh.service.IUserService;
import com.lzh.utils.SystemConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AdminUserServiceImpl implements IAdminUserService {

    @Resource
    private IUserService userService;
    @Override
    public Result list(Long current) {
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
}
