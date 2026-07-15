package com.lzh.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminCommentVO {
    /** 评论ID */
    private Long id;

    /** 作者ID */
    private Long userId;

    /** 影评ID */
    private Long reviewId;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    private Integer likeCount=0;

    /** 评状态 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
