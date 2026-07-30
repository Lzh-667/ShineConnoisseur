package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.dto.AdminLoginDTO;
import com.lzh.po.Admin;

public interface IAdminService extends IService<Admin> {
    Result login(AdminLoginDTO adminLoginDTO);

    Result logout(String token);
}
