package com.lzh.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.mapper.LikeRecordMapper;
import com.lzh.po.LikeRecord;
import com.lzh.service.ILikeRecordService;
import org.springframework.stereotype.Service;

@Service
public class LikeRecordServiceImpl extends ServiceImpl<LikeRecordMapper, LikeRecord> implements ILikeRecordService {
}
