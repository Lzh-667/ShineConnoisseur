package com.lzh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝沙箱配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    /**
     * 支付宝应用 APP_ID
     */
    private String appId;
    /**
     * 应用私钥
     */
    private String appPrivateKey;
    /**
     * 支付宝公钥
     */
    private String alipayPublicKey;
    /**
     * 支付宝网关地址
     */
    private String gatewayUrl;
    /**
     * 签名算法
     */
    private String signType = "RSA2";
    /**
     * 字符编码
     */
    private String charset = "UTF-8";
    /**
     * 数据格式
     */
    private String format = "json";
    /**
     * 异步通知地址
     */
    private String notifyUrl;
    /**
     * 同步跳转地址
     */
    private String returnUrl;
}