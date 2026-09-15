package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.VipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/vips")
@Tag(name = "会员模块", description = "会员套餐查询、会员状态查询")
public class VipController {

    @Resource
    private VipService vipService;
    @GetMapping("/products")
    @Operation(summary = "查询VIP套餐")
    public Result listProducts(){
        return vipService.products();
    }
    @GetMapping("/me")
    @Operation(summary = "查询当前用户vip信息")
    public Result getMyVip(){
        return vipService.me();
    }
}
