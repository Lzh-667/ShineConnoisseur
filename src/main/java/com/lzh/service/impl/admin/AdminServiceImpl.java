package com.lzh.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.Result;
import com.lzh.dto.AdminDTO;
import com.lzh.dto.AdminLoginDTO;
import com.lzh.mapper.AdminMapper;
import com.lzh.po.Admin;
import com.lzh.service.IAdminService;
import com.lzh.utils.PasswordEncoder;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.RegexUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements IAdminService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result login(AdminLoginDTO adminLoginDTO) {
        //1.获取用户名和密码
        String adminName = adminLoginDTO.getUsername();
        String password = adminLoginDTO.getPassword();
        if(StrUtil.isBlank(adminName)){
            //2.1.用户名不能为空
            return Result.fail("用户名不能为空");
        }
        if(RegexUtils.isPasswordInvalid(password)){
            //2.2.密码格式不符合，返回错误信息
            return Result.fail("密码格式错误");
        }
        //3.检查错误次数
        String errorKey = RedisConstants.LOGIN_ADMIN_PASSWORD_ERR_KEY + adminName;
        String errorCount = stringRedisTemplate.opsForValue().get(errorKey);
        if(StrUtil.isNotBlank(errorCount) && Integer.parseInt(errorCount) >= 5){
            return Result.fail("错误次数过多，请稍后再试");
        }
        //4.根据用户名和密码查询
        Admin admin = query().eq("username", adminName).one();
        if (admin == null || !PasswordEncoder.matches(password, admin.getPassword())) {
            Long count = stringRedisTemplate.opsForValue().increment(errorKey);
            if(Long.valueOf(1L).equals( count)){
                stringRedisTemplate.expire(
                        errorKey,
                        RedisConstants.LOGIN_ADMIN_PASSWORD_ERR_TTL,
                        TimeUnit.MINUTES
                );
            }
            //5.不存在，返回错误信息
            return Result.fail("用户名或密码错误");
        }
        stringRedisTemplate.delete(errorKey);
        //6.返回token
        return Result.ok(createToken(admin));
    }
    private String createToken(Admin admin) {
        //6.保存用户信息到redis
        //6.1.随机生成token，作为登陆令牌
        String token = UUID.fastUUID().toString(true);
        //6.2.将user对象转为HashMap存储
        AdminDTO adminDTO= BeanUtil.copyProperties(admin, AdminDTO.class);
        Map<String,Object> adminMap = BeanUtil.beanToMap(
                adminDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (fieldName, fieldValue) ->
                                        fieldValue == null ? null : fieldValue.toString()
                        )
        );
        //6.3.存储
        String tokenKey = RedisConstants.ADMIN_LOGIN_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,adminMap);
        //6.4.设置token有效期
        stringRedisTemplate.expire(tokenKey,RedisConstants.ADMIN_LOGIN_TTL,TimeUnit.MINUTES);
        return token;
    }


    @Override
    public Result logout() {
        return Result.ok();
    }
}
