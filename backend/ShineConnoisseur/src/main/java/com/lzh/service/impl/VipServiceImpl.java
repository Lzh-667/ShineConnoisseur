package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.mapper.UserVipMapper;
import com.lzh.mapper.VipProductMapper;
import com.lzh.po.UserVip;
import com.lzh.po.VipProduct;
import com.lzh.service.VipService;
import com.lzh.utils.UserHolder;
import com.lzh.vo.UserVipVO;
import com.lzh.vo.VipProductVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class VipServiceImpl implements VipService {

    @Resource
    private VipProductMapper vipProductMapper;
    @Resource
    private UserVipMapper userVipMapper;
    @Override
    public Result products() {
        List<VipProduct> vpl = vipProductMapper.getProducts();
        if(vpl.isEmpty()){
            return Result.ok(new PageResult<>(0L, Collections.emptyList()));
        }
        List<VipProductVO> voList = vpl.stream()
                .map(product -> BeanUtil.copyProperties(product,VipProductVO.class))
                .toList();
        return Result.ok(new PageResult<>((long)voList.size(),voList));
    }
    @Override
    public Result me() {
        Long userId = UserHolder.getUser().getId();
        UserVip userVip = userVipMapper.selectOne(
                new LambdaQueryWrapper<UserVip>()
                        .eq(UserVip::getUserId,userId)
        );
        if(userVip==null){
            UserVipVO vo = new UserVipVO();
            vo.setVip(false);
            return Result.ok(vo);
        }
        UserVipVO vo = BeanUtil.copyProperties(userVip, UserVipVO.class);
        LocalDateTime now = LocalDateTime.now();
        vo.setVip(vo.getExpireTime().isAfter(now));
        return Result.ok(vo);
    }
}
