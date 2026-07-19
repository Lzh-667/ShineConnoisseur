package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.dto.LoginFormDTO;
import com.lzh.dto.RegisterFormDTO;
import com.lzh.dto.UpdatePasswordDTO;
import com.lzh.dto.UpdateProfileDTO;
import com.lzh.po.User;

public interface IUserService extends IService<User> {
    Result sendLoginCode(String phone);

    Result loginByCode(LoginFormDTO loginForm);

    Result loginByPassword(LoginFormDTO loginForm);

    Result register(RegisterFormDTO registerFormDTO);

    Result sendRegisterCode(String phone);

    Result info(Long id);

    Result logout(String token);

    Result updateProfile(UpdateProfileDTO updateProfileDTO);

    Result updatePassword(UpdatePasswordDTO dto);
}
