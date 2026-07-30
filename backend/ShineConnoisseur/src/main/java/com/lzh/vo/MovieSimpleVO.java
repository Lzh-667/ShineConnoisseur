package com.lzh.vo;

import lombok.Data;

@Data
public class MovieSimpleVO {
    private Long id;
    /** 电影名称 */
    private String title;
    /** 海报URL */
    private String cover;
}
