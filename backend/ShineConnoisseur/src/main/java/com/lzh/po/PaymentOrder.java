package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("payment_order")
public class PaymentOrder {
    /** 订单ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 业务订单号 */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** VIP套餐ID */
    private Long productId;
    /** 下单时的VIP套餐时长快照，单位：天 */
    private Integer durationDays;
    /** 订单金额 */
    private BigDecimal amount;
    /** 支付方式：1支付宝，2微信 */
    private Integer paymentMethod;
    /** 订单状态：0待支付，1支付成功，2已关闭，3已退款 */
    private Integer status;
    /** 第三方支付交易号 */
    private String transactionId;
    /** 订单支付截止时间 */
    private LocalDateTime expireTime;
    /** 支付成功时间 */
    private LocalDateTime payTime;
    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
