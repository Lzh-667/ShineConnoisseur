package com.lzh.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminMovieVO {

    private Long id;
    /** 电影名称 */
    private String title;

    /** 原名（外文） */
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
    private LocalDate releaseDate;

    /** 片长（分钟） */
    private Integer duration;

    /** 剧情简介 */
    private String summary;

    /** 评分总分 */
    private BigDecimal ratingSum;

    /** 评分人数 */
    private Integer ratingCount;

    /** 状态：0-下架，1-上架 */
    private Integer status;

    private LocalDateTime createTime;

}
