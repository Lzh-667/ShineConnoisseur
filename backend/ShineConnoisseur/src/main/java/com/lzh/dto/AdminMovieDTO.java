package com.lzh.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "电影管理表单")
public class AdminMovieDTO {
    @Schema(description = "电影名称")
    private String title;
    @Schema(description = "原名（外文）")
    private String originalTitle;
    @Schema(description = "海报URL")
    private String cover;
    @Schema(description = "导演，逗号分隔")
    private String director;
    @Schema(description = "演员，逗号分隔")
    private String actors;
    @Schema(description = "类型，逗号分隔")
    private String genre;
    @Schema(description = "地区")
    private String region;
    @Schema(description = "语言")
    private String language;
    @Schema(description = "上映日期")
    private LocalDate releaseDate;
    @Schema(description = "片长（分钟）")
    private Integer duration;
    @Schema(description = "剧情简介")
    private String summary;
}
