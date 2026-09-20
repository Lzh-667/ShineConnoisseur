package com.lzh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.pay")
public class WeChatPayConfig {
    /**
     * 微信支付应用 ID
     */
    private String appId;
    /**
     * 微信支付商户号
     */
    private String merchantId;
    /**
     * 商户API私钥
     */
    private String privateKeyPath;
    /**
     * 商户API证书序列号
     */
    private String merchantSerialNumber;
    /**
     * APIv3密钥
     */
    private String apiV3Key;
    /**
     * 支付成功回调地址
     */
    private String notifyUrl;
}
