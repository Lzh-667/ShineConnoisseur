package com.lzh.controller;

import com.lzh.service.VipService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/vips")
@Tag(name = "会员模块", description = "会员套餐查询、会员状态查询")
public class VipController {

    @Resource
    private VipService vipService;

}
