package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private final StringRedisTemplate stringRedisTemplate;

    //构造函数注入
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        //JSONUtil.toJsonStr(value) : 将object类型的value序列化为json字符串
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit); //key,值,时间,时间单位
    }

    //逻辑过期
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);  //设置值
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));  //设置逻辑过期时间
        //写入redis
        //JSONUtil.toJsonStr(redisData):将redisData序列化为json字符串
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /*
     * 缓存穿透
     * */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis中查询店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否存在
        if (StrUtil.isNotBlank(json)) { //StrUtil.isNotBlank()判断某字符串是否不为空,且长度不为0,且不由空白符(whitespace)构成
            //3.存在,返回店铺信息(JSONUtil.toBean:将json转为bean)
            return JSONUtil.toBean(json, type);
        }
        //(不存在,shopJson为null或者空字符串)判断命中的是否为空值
        if (json != null) {
            //shopJson为空字符串时,返回错误信息
            return null;
        }
        //4.shopJson为null时,根据id查询数据库
        R r = dbFallback.apply(id);
        //5.判断店铺是否存在
        if (r == null) {
            //6.不存在,将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //7.存在,将店铺信息存入redis中(添加随机时间防止缓存雪崩)
        this.set(key, r, time, unit);  //key,查询到的数据库信息,时间,时间单位
        //8.返回
        return r;
    }
    //线程池
    private static final ExecutorService CACHE_REBUTLD_EXECUTOR = Executors.newFixedThreadPool(10);
    //逻辑过期解决缓存击穿
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否存在
        if (StrUtil.isBlank(shopJson)) { //StrUtil.isBlank() : 判断某字符串是否为空或长度为0或由空白符(whitespace)构成
            //不存在,返回空
            return null;
        }
        //3.命中(存在),需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        LocalDateTime expireTime = redisData.getExpireTime(); //逻辑过期时间
        //5.判断缓存是否过期(expireTime是否在当前时间LocalDateTime.now())之后)
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期,返回店铺信息
            return r;
        }
        //5.2.已过期,需要缓存重建
        //6.缓存重建
        //6.1.获取互斥锁
        String lockkey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockkey);
        //6.2.判断是否获取锁成功
        if (isLock) {
            //6.3.成功,开启独立线程实现缓存重建
            CACHE_REBUTLD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit); //key,查询到的数据库信息,时间,时间单位
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockkey);
                }
            });
        }
        //6.4.失败,返回过期的商铺信息
        return r;
    }
    /*
     * 获取锁
     * */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    /*
     * 释放锁
     * */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
