package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 电影ID */
    @TableField("movie_id")
    private Long movieId;

    /** 评分 1-10 */
    private Integer rating;

    /** 影评标题 */
    private String title;

    /** 影评内容 */
    private String content;

    /** 是否剧透：0-否，1-是 */
    private Integer spoiler;

    /** 点赞数 */
    @TableField("like_count")
    private Integer likeCount=0;

    /** 状态：0-删除，1-正常，2-审核中 */
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
