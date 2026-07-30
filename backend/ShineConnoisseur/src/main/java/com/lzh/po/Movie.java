package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("movie")
public class Movie {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 电影名称 */
    private String title;

    /** 原名（外文） */
    @TableField("original_title")
    private String originalTitle;

    /** 海报URL */
    private String cover;

    /** 导演，逗号分隔 */
    private String director;

    /** 演员，逗号分隔 */
    private String actors;

    /** 类型，逗号分隔 */
    private String genre;

    /** 地区 */
    private String region;

    /** 语言 */
    private String language;

    /** 上映日期 */
    @TableField("release_date")
    private LocalDate releaseDate;

    /** 片长（分钟） */
    private Integer duration;

    /** 剧情简介 */
    private String summary;

    /** 评分总分 */
    @TableField("rating_sum")
    private BigDecimal ratingSum;

    /** 评分人数 */
    @TableField("rating_count")
    private Integer ratingCount;

    /** 状态：0-下架，1-上架 */
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
