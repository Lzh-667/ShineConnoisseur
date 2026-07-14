package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.Result;
import com.lzh.dto.LoginFormDTO;
import com.lzh.dto.RegisterFormDTO;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.UserMapper;
import com.lzh.po.User;
import com.lzh.service.IUserService;
import com.lzh.utils.PasswordEncoder;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.RegexUtils;
import com.lzh.vo.UserInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendLoginCode(String phone) {
        //1.验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2。不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.检查是否频繁发送
        String codeKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String existCode = stringRedisTemplate.opsForValue().get(codeKey);
        if(existCode != null){
            return Result.fail("发送过于频繁，请稍后再试");
        }
        //4.判断用户是否存在
        User user = query().eq("phone", phone).one();
        if(user == null){
            //5.用户不存在
            return Result.ok();
        }
        //6.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //7.保存验证码到redis
        stringRedisTemplate.opsForValue().set(codeKey,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //8.发送验证码
        log.info("发送登录验证码成功：{}",code);
        //返回
        return Result.ok();
    }

    @Override
    public Result loginByCode(LoginFormDTO loginForm) {
        //1.验证手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.检查错误次数
        String errorKey = RedisConstants.LOGIN_USER_CODE_ERR_KEY + phone;
        String errorCount = stringRedisTemplate.opsForValue().get(errorKey);
        if(StrUtil.isNotBlank(errorCount) && Integer.parseInt(errorCount) >= 5){
            return Result.fail("错误次数过多，请稍后再试");
        }
        //3.校验验证码
        String code = loginForm.getCode();
        String codeKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(codeKey);
        if(!StrUtil.equals(code, cacheCode)){
            Long count = stringRedisTemplate.opsForValue().increment(errorKey);
            if(Long.valueOf(1L).equals( count)){
                stringRedisTemplate.expire(
                        errorKey,
                        RedisConstants.LOGIN_USER_CODE_ERR_TTL,
                        TimeUnit.MINUTES
                );
            }
            //4.不一致，返回错误信息
            return Result.fail("验证码错误");
        }
        // 验证通过后立即删除，防止重复使用
        stringRedisTemplate.delete(codeKey);
        // 验证成功后删除错误次数
        stringRedisTemplate.delete(errorKey);
        //5.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //6.判断用户是否存在
        if(user == null){
            //7.不存在，返回错误信息
            return Result.fail("手机号或验证码错误");
        }
        //8.返回token
        return Result.ok(createToken(user));
    }

    @Override
    public Result loginByPassword(LoginFormDTO loginForm) {
        //1.获取用户名和密码
        String username = loginForm.getUsername();
        String password = loginForm.getPassword();
        if(StrUtil.isBlank(username)){
            //2.1.用户名不能为空
            return Result.fail("用户名不能为空");
        }
        if(RegexUtils.isPasswordInvalid(password)){
            //2.2.密码格式不符合，返回错误信息
            return Result.fail("密码格式错误");
        }
        //3.检查错误次数
        String errorKey = RedisConstants.LOGIN_USER_PASSWORD_ERR_KEY + username;
        String errorCount = stringRedisTemplate.opsForValue().get(errorKey);
        if(StrUtil.isNotBlank(errorCount) && Integer.parseInt(errorCount) >= 5){
            return Result.fail("错误次数过多，请稍后再试");
        }
        //4.根据用户名和密码查询
        User user = query().eq("username", username).one();
        if (user == null || !PasswordEncoder.matches(password, user.getPassword())) {
            Long count = stringRedisTemplate.opsForValue().increment(errorKey);
            if(Long.valueOf(1L).equals( count)){
                stringRedisTemplate.expire(
                        errorKey,
                        RedisConstants.LOGIN_USER_PASSWORD_ERR_TTL,
                        TimeUnit.MINUTES
                );
            }
            //5.不存在，返回错误信息
            return Result.fail("用户名或密码错误");
        }
        stringRedisTemplate.delete(errorKey);
        //6.返回token
        return Result.ok(createToken(user));
    }

    @Override
    public Result register(RegisterFormDTO registerFormDTO) {
        //1.检查表单项是否为空或违规
        String username = registerFormDTO.getUsername();
        String password = registerFormDTO.getPassword();
        String confirmPassword = registerFormDTO.getConfirmPassword();
        String phone = registerFormDTO.getPhone();
        String email = registerFormDTO.getEmail();
        if(StrUtil.isBlank(username)){
            return Result.fail("用户名不能为空");
        }
        if(RegexUtils.isPasswordInvalid(password)){
            return Result.fail("密码格式错误");
        }
        if(!StrUtil.equals(password, confirmPassword)){
            return Result.fail("两次输入密码不一致");
        }
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        if(StrUtil.isNotBlank(email)&&RegexUtils.isEmailInvalid(email)){
            return Result.fail("邮箱格式错误");
        }
        //2.检查错误次数
        String errorKey = RedisConstants.REGISTER_USER_ERR_KEY + phone;
        String errorCount = stringRedisTemplate.opsForValue().get(errorKey);
        if(StrUtil.isNotBlank(errorCount) && Integer.parseInt(errorCount) >= 5){
            return Result.fail("错误次数过多，请稍后再试");
        }
        //3.检查用户名、手机、邮箱是否存在
        User user;
        user=query().eq("username", username).one();
        if(user != null){
            //4.1.若存在，返回错误信息
            return Result.fail("用户名已存在");
        }
        user=query().eq("phone", phone).one();
        if(user != null){
            //4.2.若存在，返回错误信息
            return Result.fail("手机号已存在");
        }
        if(StrUtil.isNotBlank(email)){
            user=query().eq("email", email).one();
            if(user != null){
                //4.3.已存在，返回错误信息
                return Result.fail("邮箱已存在");
            }
        }
        //5.校验验证码
        String code = registerFormDTO.getCode();
        String codeKey = RedisConstants.REGISTER_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(codeKey);
        if(!StrUtil.equals(code, cacheCode)){
            Long count = stringRedisTemplate.opsForValue().increment(errorKey);
            if(Long.valueOf(1L).equals( count)){
                stringRedisTemplate.expire(
                        errorKey,
                        RedisConstants.REGISTER_USER_ERR_TTL,
                        TimeUnit.MINUTES
                );
            }
            //6.不正确，返回错误信息
            return Result.fail("验证码错误");
        }
        //7.删除验证码和错误次数
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(errorKey);
        //8.创建用户
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(PasswordEncoder.encode(password));
        newUser.setEmail(email);
        newUser.setPhone(phone);
        newUser.setNickname(username+RandomUtil.randomNumbers(4));
        save(newUser);
        //9.返回用户信息
        return Result.ok(createToken(newUser));
    }

    @Override
    public Result sendRegisterCode(String phone) {
        //1.验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2。不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.检查是否频繁发送
        String codeKey = RedisConstants.REGISTER_CODE_KEY + phone;
        String existCode = stringRedisTemplate.opsForValue().get(codeKey);
        if(existCode != null){
            return Result.fail("发送过于频繁，请稍后再试");
        }
        //4.判断用户是否存在
        User user = query().eq("phone", phone).one();
        if(user != null){
            //5.用户已存在
            return Result.fail("手机号已注册");
        }
        //6.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //7.保存验证码到redis
        stringRedisTemplate.opsForValue().set(codeKey,code,RedisConstants.REGISTER_CODE_TTL, TimeUnit.MINUTES);
        //8.发送验证码
        log.info("发送注册验证码成功：{}",code);
        //返回
        return Result.ok();
    }
    @Override
    public Result info(Long id) {
        User user = getById(id);
        if(user==null){
            return Result.fail("用户不存在");
        }
        UserInfo info = new UserInfo();
        BeanUtils.copyProperties(user,info);
        return Result.ok(info);
    }
    private String createToken(User user) {
        //1.保存用户信息到redis
        //1.1.随机生成token，作为登陆令牌
        String token = UUID.fastUUID().toString(true);
        //1.2.将user对象转为HashMap存储
        UserDTO userDTO= BeanUtil.copyProperties(user, UserDTO.class);
        Map<String,Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (fieldName, fieldValue) ->
                                        fieldValue == null ? null : fieldValue.toString()
                        )
        );
        //1.3.存储
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //1.4.设置token有效期
        stringRedisTemplate.expire(tokenKey,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        return token;
    }

}
