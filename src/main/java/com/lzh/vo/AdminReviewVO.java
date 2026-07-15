package com.lzh.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReviewVO {
    /** 影评ID */
    private Long id;

    /** 评分 1-10 */
    private Integer rating;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 作者用户ID */
    private Long userId;

    /** 电影ID */
    private Long movieId;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数量 */
    private Integer commentCount;

    /** 状态：0-删除，1-正常，2-审核中 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

}
