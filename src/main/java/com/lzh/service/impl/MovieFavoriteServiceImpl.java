package com.lzh.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.mapper.MovieFavoriteMapper;
import com.lzh.po.MovieFavorite;
import com.lzh.service.IMovieFavoriteService;
import org.springframework.stereotype.Service;

@Service
public class MovieFavoriteServiceImpl extends ServiceImpl<MovieFavoriteMapper, MovieFavorite> implements IMovieFavoriteService {
}
