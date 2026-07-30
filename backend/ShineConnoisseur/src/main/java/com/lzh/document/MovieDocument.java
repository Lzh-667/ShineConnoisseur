package com.lzh.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "movie")
public class MovieDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String originalTitle;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String director;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String actors;

    @Field(type = FieldType.Keyword)
    private String genre;

    @Field(type = FieldType.Keyword)
    private String region;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String summary;

    private String cover;

    private Integer status;
}
