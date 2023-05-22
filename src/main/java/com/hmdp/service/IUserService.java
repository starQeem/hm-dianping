package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    //短信验证登录
    public Result sendCode(String phone, HttpSession session);
    //登录功能
    public Result login(LoginFormDTO loginForm, HttpSession session);
    //用户签到
    Result sign();
    //统计连续签到
    Result signCount();
}
