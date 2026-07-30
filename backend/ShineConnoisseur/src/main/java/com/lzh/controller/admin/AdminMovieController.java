package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.dto.AdminMovieDTO;
import com.lzh.service.IAdminMovieService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/admins/movies")
@Tag(name = "管理端-电影", description = "电影发布、编辑、上下架")
public class AdminMovieController {

    @Resource
    private IAdminMovieService adminMovieService;
    @Operation(summary = "电影列表")
    @GetMapping("/list")
    public Result listMovies(@RequestParam(value = "current",defaultValue = "1") Integer current){
        return adminMovieService.listMovies(current);
    }
    @Operation(summary = "发布电影")
    @PostMapping("/publish")
    public Result publishMovie(@RequestBody AdminMovieDTO movieDTO){
        return adminMovieService.publishMovie(movieDTO);
    }
    @Operation(summary = "编辑电影")
    @PutMapping("/update/{id}")
    public Result updateMovie(
            @RequestBody AdminMovieDTO movieDTO,
            @PathVariable("id") Long id
    ){
        return adminMovieService.updateMovie(movieDTO,id);
    }
    @Operation(summary = "上下架切换")
    @PutMapping("/status/{id}")
    public Result updateMovieStatus(@PathVariable("id") Long id){
        return adminMovieService.updateMovieStatus(id);
    }
}
