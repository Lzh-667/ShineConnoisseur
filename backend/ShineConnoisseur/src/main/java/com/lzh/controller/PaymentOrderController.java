package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.IPaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/payments")
@Tag(name = "支付模块")
public class PaymentOrderController {

    @Resource
    private IPaymentOrderService paymentOrderService;

    @PostMapping("/orders")
    @Operation(summary = "创建支付订单")
    public Result createOrder(@RequestParam Long productId,
                              @RequestParam Integer paymentMethod){
        return paymentOrderService.createOrder(productId,paymentMethod);
    }
}
