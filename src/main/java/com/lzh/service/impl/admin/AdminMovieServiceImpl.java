package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.dto.AdminMovieDTO;
import com.lzh.po.Movie;
import com.lzh.service.IAdminMovieService;
import com.lzh.service.IMovieService;
import com.lzh.utils.SystemConstants;
import com.lzh.vo.AdminMovieVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class AdminMovieServiceImpl implements IAdminMovieService {
    @Resource
    private IMovieService movieService;
    @Override
    public Result listMovies(Long current) {
        //1.查询电影列表
        Page<Movie> page = movieService.query()
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //2.转换为VO
        List<AdminMovieVO> vos = page.getRecords()
                .stream()
                .map(movie -> {
                    AdminMovieVO vo = new AdminMovieVO();
                    BeanUtils.copyProperties(movie, vo);
                    return vo;
                })
                .toList();
        //3.封装并返回
        PageResult<AdminMovieVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(vos);
        return Result.ok(result);
    }
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
