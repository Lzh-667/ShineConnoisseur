package com.lzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzh.po.VipProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VipProductMapper extends BaseMapper<VipProduct> {
    @Select("""
        select id,name,duration_days,price
        from vip_product
        where status=1
    """)
    List<VipProduct> getProducts();
}
