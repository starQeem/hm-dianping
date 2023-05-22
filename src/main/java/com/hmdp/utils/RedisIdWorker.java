package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /*
    * 开始时间戳
    * */
    private static final long BEGIN_TIMESTAMP = 1679656680;
    /*
    * 序列号的位数
    * */
    private static final int  COUNT_BITS = 32;

    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now(); //获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);//指定为秒数
        long timestamp = nowSecond - BEGIN_TIMESTAMP;//获得时间戳
        //2.生成序列号
        //2.1.获取当前时间,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));//一天一个key
        //2.2.自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接并返回(或运算拼接)
        return timestamp << COUNT_BITS | count; //让时间戳向左移动
    }

//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2023, 3, 24, 11, 18);
//        long second = time.toEpochSecond(ZoneOffset.UTC);//指定为秒数
//        System.out.println(second);
//
//    }

}
