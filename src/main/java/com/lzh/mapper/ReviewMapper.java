package com.lzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.po.Review;
import org.apache.ibatis.annotations.Select;

public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT * FROM review WHERE status IN (1, 2) ORDER BY create_time DESC")
    IPage<Review> selectAdminPage(Page<Review> page);
}
