package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lzh.common.Result;
import com.lzh.mapper.VipProductMapper;
import com.lzh.po.VipProduct;
import com.lzh.service.VipService;
import com.lzh.vo.VipProductVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class VipServiceImpl implements VipService {

    @Resource
    private VipProductMapper vipProductMapper;
    @Override
    public Result products() {
        List<VipProduct> vpl = vipProductMapper.getProducts();
        List<VipProductVO> voList = vpl.stream()
                .map(product -> BeanUtil.copyProperties(product,VipProductVO.class))
                .toList();
        return Result.ok(voList);
    }
    @Override
    public Result me() {
        return null;
    }
}
