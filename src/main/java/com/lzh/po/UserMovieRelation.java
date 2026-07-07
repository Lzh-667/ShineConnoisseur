package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_movie_relation")
public class UserMovieRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("movie_id")
    private Long movieId;

    /** 0-未看，1-想看，2-在看，3-看过 */
    @TableField("watch_status")
    private Integer watchStatus;

    /** 0-未收藏，1-已收藏 */
    @TableField("is_favorite")
    private Integer isFavorite;

    /** 个人标签 */
    private String tags;

    /** 观看日期 */
    @TableField("watched_date")
    private LocalDate watchedDate;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}