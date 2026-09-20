package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.AlipayService;
import com.lzh.service.IPaymentOrderService;
import com.lzh.service.WechatPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
@Tag(name = "支付模块")
public class PaymentOrderController {

    @Resource
    private IPaymentOrderService paymentOrderService;
    @Resource
    private AlipayService alipayService;
    @Resource
    private WechatPayService wechatPayService;
    @PostMapping("/orders")
    @Operation(summary = "创建支付订单")
    public Result createOrder(@RequestParam Long productId,
                              @RequestParam Integer paymentMethod,
                              @RequestHeader("Idempotency-Key") String requestId){
        return paymentOrderService.createOrder(productId, paymentMethod, requestId);
    }
    @PostMapping("/alipay")
    @Operation(summary = "支付宝支付")
    public Result alipay(@RequestParam String orderNo){
        return alipayService.alipay(orderNo);
    }
    @PostMapping("/alipay/notify")
    @Operation(summary = "支付宝异步通知notify")
    public ResponseEntity<String> alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            String value = "";
            for (String v : values) {
                value = value + v + ",";
            }
            if (value.length() > 0) {
                value = value.substring(0, value.length() - 1);
            }
            params.put(name, value);
        }
        try {
            boolean handled = alipayService.notify(params);
            return ResponseEntity.ok(handled ? "success" : "failure");
        } catch (RuntimeException e) {
            log.error("支付宝回调处理失败，将通知支付宝重试", e);
            return ResponseEntity.internalServerError().body("failure");
        }
    }
    @PostMapping("/wechat")
    @Operation(summary = "微信支付")
    public Result wechat(@RequestParam String orderNo){
        return wechatPayService.wechat(orderNo);
    }
    @PostMapping("/wechat/notify")
    @Operation(summary = "微信异步通知notify")
    public ResponseEntity<String> wechatNotify(
            @RequestBody String body,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Serial") String serialNumber) {
        try {
            boolean handled = wechatPayService.notify(
                    body,
                    signature,
                    timestamp,
                    nonce,
                    serialNumber
            );
            if (handled) {
                return ResponseEntity.noContent().build();
            }
        } catch (RuntimeException e) {
            log.error("微信回调处理失败，将通知微信重试", e);
        }
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"FAIL\",\"message\":\"处理失败\"}");
    }
    @GetMapping("/show")
    @Operation(summary = "订单查询")
    public Result showOrder(@RequestParam String orderNo){
        return paymentOrderService.showOrder(orderNo);
    }
    @DeleteMapping("/delete")
    @Operation(summary = "订单取消")
    public Result deleteOrder(@RequestParam String orderNo){
        return paymentOrderService.deleteOrder(orderNo);
    }
}
