package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("review_comment")
public class ReviewComment {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 所属影评ID */
    @TableField("review_id")
    private Long reviewId;

    /** 一级评论 */
    @TableField("root_id")
    private Long rootId;

    /** 被回复用户*/
    @TableField("reply_user_id")
    private Long replyUserId;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    @TableField("like_count")
    private Integer likeCount=0;

    /** 状态：0-删除，1-正常 */
    @TableLogic(value = "1", delval = "0")
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
