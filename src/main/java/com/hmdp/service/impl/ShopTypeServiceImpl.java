package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService typeService;

    //查询店铺类型
    @Override
    public Result queryList() {
        //1.从redis中查询店铺类型
        String shopTypeJson = stringRedisTemplate.opsForValue().get(LOCK_SHOP_KEY);
        //2.判断缓存是否存在
        if (StrUtil.isNotBlank(shopTypeJson)){
            //3.存在,返回店铺信息
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //4.不存在,查询数据库
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        //5.判断店铺类型是否存在
        if (typeList == null){
            //6.不存在,返回错误
            return Result.fail("店铺类型不存在!");
        }
        //7.存在,将店铺类型存入redis中
        stringRedisTemplate.opsForValue().set(LOCK_SHOP_KEY,JSONUtil.toJsonStr(typeList),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //8.返回
        return Result.ok(typeList);
    }
}
