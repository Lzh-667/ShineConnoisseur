package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.dto.AdminMovieDTO;
import com.lzh.service.IAdminMovieService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admins/movies")
public class AdminMovieController {

    @Resource
    private IAdminMovieService adminMovieService;
    @GetMapping("/list")
    public Result listMovies(@RequestParam(value = "current",defaultValue = "1") Long current){
        return adminMovieService.listMovies(current);
    }
    @PostMapping("/publish")
    public Result publishMovie(@RequestBody AdminMovieDTO movieDTO){
        return adminMovieService.publishMovie(movieDTO);
    }
    @PutMapping("/update/{id}")
    public Result updateMovie(
            @RequestBody AdminMovieDTO movieDTO,
            @PathVariable("id") Long id
    ){
        return adminMovieService.updateMovie(movieDTO,id);
    }
}
