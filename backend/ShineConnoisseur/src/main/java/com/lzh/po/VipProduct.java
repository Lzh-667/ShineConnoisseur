package com.lzh.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("vip_product")
public class VipProduct {
    /**
     * 套餐ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 套餐名称
     */
    private String name;
    /**
     * 会员时长，单位：天
     */
    private Integer durationDays;
    /**
     * 套餐价格
     */
    private BigDecimal price;
    /**
     * 状态：0下架，1上架
     */
    private Integer status;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}