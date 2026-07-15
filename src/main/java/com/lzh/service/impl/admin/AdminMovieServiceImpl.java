package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lzh.common.Result;
import com.lzh.dto.AdminMovieDTO;
import com.lzh.po.Movie;
import com.lzh.service.IAdminMovieService;
import com.lzh.service.IMovieService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class AdminMovieServiceImpl implements IAdminMovieService {
    @Resource
    private IMovieService movieService;
    @Override
    public Result publishMovie(AdminMovieDTO movieDTO) {
        //1.将dto转化为movie
        Movie movie = new Movie();
        BeanUtils.copyProperties(movieDTO,movie);
        //2.保存到数据库
        boolean success = movieService.save(movie);
        if(!success){
            log.info("添加电影失败");
            return Result.fail("添加电影失败");
        }
        return Result.ok();
    }

    @Override
    public Result updateMovie(AdminMovieDTO movieDTO,Long movieId) {
        if(movieService.getById(movieId) == null){
            log.info("电影不存在");
            return Result.fail("电影不存在");
        }
        //1.创建movie对象
        Movie movie = new Movie();
        BeanUtils.copyProperties(movieDTO,movie);
        movie.setId(movieId);
        //2.修改数据
        boolean success = movieService.updateById(movie);
        if(!success){
            log.info("修改电影失败");
            return Result.fail("修改电影失败");
        }
        return Result.ok();
    }
}
