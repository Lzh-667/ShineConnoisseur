package com.lzh.dto;

import lombok.Data;

@Data
public class ReviewDTO {

    private Integer rating;
    private String title;
    private String content;
    private Integer spoiler;
}
