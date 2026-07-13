package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接受信息的用户 */
    @TableField("user_id")
    private Long userId;

    /** 触发消息的用户 */
    @TableField("from_user_id")
    private Long fromUserId;

    /**
     * 消息类型
     * 0-关注
     * 1-点赞影评
     * 2-评论影评
     * 3-点赞评论
     * 4-回复评论
     * */
    private Integer type;

    /**
     * 目标类型
     * 0-用户
     * 1-影评
     * 2-评论
     */
    @TableField("target_type")
    public Integer targetType;

    /** 关联业务id */
    @TableField("target_id")
    private Long targetId;

    /** 消息内容 */
    private String content;

    /** 是否已读 0-未读 1-已读 */
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
