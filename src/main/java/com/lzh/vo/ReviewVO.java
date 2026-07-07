package com.lzh.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewVO {
    /** 影评ID */
    private Long id;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 作者用户ID */
    private Long userId;

    /** 作者用户名 */
    private String userName;

    /** 昵称 */
    private String nickName;

    /** 作者头像 */
    private String avatar;

    /** 点赞数 */
    private Integer likeCount;

    /** 当前用户是否点赞 */
    private Boolean isLike;

    /** 是否可以编辑 */
    private Boolean canEditAndDelete;

    /** 评论数量 */
    private Integer commentCount;

    /** 创建时间 */
    private LocalDateTime createTime;

}
