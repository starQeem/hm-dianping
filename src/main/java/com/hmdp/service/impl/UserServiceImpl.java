package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
     * 短信验证登录
     * */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号(调用RegexUtils校验手机号格式是否有效)
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果校验失败,则返回手机号格式错误
            return Result.fail("手机号格式错误!");
        }
        //3.校验成功,生成6位数随机验证码(使用hutool包下的RandomUtil工具类随机数生成)
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis中,设置有效期为2分钟
        // (已经在RedisConstants工具类中优雅的定义LOGIN_CODE_KEY(login:code:)和LOGIN_CODE_TTL(2))
        //redis语句: set login:code:手机号码 code 有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送验证码成功,验证码: {}", code);
        //6.返回ok
        return Result.ok();
    }

    /*
     * 登录功能
     * */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //1.校验手机号(调用RegexUtils校验手机号格式是否有效)
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.校验失败,则返回手机号格式错误
            return Result.fail("手机号格式错误!");
        }
        //2.从redis中获取验证码并校验(redis语句:  get login:code:手机号码  )
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();  //页面输入的验证码
        //先验证redis中验证码是否还存在(没过期),存在再验证输入的验证码与redis中的是否一致
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3.验证码不一致,报错
            return Result.fail("验证码错误");
        }
        //4.验证码一致,根据手机号查询用户(使用mp中的query查询一条记录)
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user == null) {
            //6.不存在(查询结果为空),创建新用户
            user = createUserWithPhone(phone);  //将创建的新用户存在user对象中
        }
        // 7.存在,保存用户到redis中
        // 7.1.随机生成token,作为登录令牌(使用hutool包下的randomUUID()生成token并转为string类型)
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为Hash存储
        // (使用hutool包下的BeanUtil工具类的方法将user对象中的数据拷贝到UserDTO中(因为user记录有密码等信息,防止信息泄露拷贝到UserDTO中就只有id,用户名和头像信息了))
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将userDTO转为Map类型的userMap(因为下面7.3需要将userDTO存入redis中的hash结构中,userDTO是string类型,存不了)
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储(定义常量 login:token:随机生成的token ,使得代码看起来更优雅 )
        String tokenKey = LOGIN_USER_KEY + token;
        // redis语句: hset login:token:随机生成的token 用户信息(包括id,用户名,头像)
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期(redis语句: ttl login:token:随机生成的token 30分钟)
        stringRedisTemplate.expire(tokenKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 8.返回token
        return Result.ok(token);
    }


    /*
     * 创建新用户
     * */
    User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);  //将手机号存入数据库中
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));  //将用户名存入数据库中
        //2.保存用户
        save(user);
        return user;
    }

    /*
     * 用户签到
     * */
    @Override
    public Result sign() {
        //1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入redis setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }
    /*
    * 统计连续签到
    * */
    @Override
    public Result signCount() {
        //1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截止今天为止的所有签到记录,返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()){
            //没有任何签到结果
            return Result.ok();
        }
        Long num = result.get(0);
        if (num == null || num == 0){
            return Result.ok(0);
        }
        //6.循环遍历
        int count = 0;
        while (true){
            //6.1.让这个数字与1做运算,得到的数字的最后一位bit位
            if ((num & 1) == 0){    //判断这个bit位是否为0
                //如果为0,说明未签到,结束
                break;
            }else {
                //如果不为0,说明已经签到,计数器+1
                count++;
            }
            //把数字右移一位,抛弃最后一个bit位,继续下一个bit位
            num>>>= 1 ;
        }
        return Result.ok(count);
    }


}
