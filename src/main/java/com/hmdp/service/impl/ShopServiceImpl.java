package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /*
     * 查询店铺
     * */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿(key,id,类型,根据id查询数据库函数,过期时间,时间单位)
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            Shop shop1 = getById(id);
            return Result.ok(shop1);
//            return Result.fail("店铺不存在!");
        }
        //8.返回
        return Result.ok(shop);
    }
    //线程池
//    private static final ExecutorService CACHE_REBUTLD_EXECUTOR = Executors.newFixedThreadPool(10);
////    //逻辑过期解决缓存击穿
//    public Shop queryWithLogicalExpire(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis中查询店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断缓存是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            //不存在,返回空
//            return null;
//        }
//        //3.命中(存在),需要把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.判断缓存是否过期(expireTime是否在当前时间LocalDateTime.now())之后)
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //5.1.未过期,返回店铺信息
//            return shop;
//        }
//        //5.2.已过期,需要缓存重建
//        //6.缓存重建
//        //6.1.获取互斥锁
//        String lockey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockey);
//        //6.2.判断是否获取锁成功
//        if (isLock) {
//            //6.3.成功,开启独立线程实现缓存重建
//            CACHE_REBUTLD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    //释放锁
//                    unLock(lockey);
//                }
//            });
//        }
//        //6.4.失败,返回过期的商铺信息
//        return shop;
//    }
//    /*
//     * 互斥锁解决缓存击穿
//     * */
//    public Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis中查询店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断缓存是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.存在,返回店铺信息
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //(不存在,shopJson为null或者空字符串)判断命中的是否为空值
//        if (shopJson != null) {
//            //shopJson为空字符串时,返回错误信息
//            return null;
//        }
//        //4实现缓存重建
//        //4.1.获取互斥锁
//        String lockkey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockkey);
//            //4.2.判断是否成功
//            if (!isLock) {
//                //4.3.失败,失眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //4.4.成功,根据id查询数据库
//            shop = getById(id);
//            //模拟重建延时
//            Thread.sleep(200);
//            //5.判断店铺是否存在
//            if (shop == null) {
//                //6.不存在,将空值写入redis
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                //返回错误信息
//                return null;
//            }
//            //7.存在,将店铺信息存入redis中(添加随机时间防止缓存雪崩)
//            stringRedisTemplate.opsForValue()
//                    .set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + RandomUtil.randomInt(5), TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //8.释放互斥锁
//            unLock(lockkey);
//        }
//        //9.返回
//        return shop;
//    }

    /*
     * 缓存穿透
     * */
//    public Shop queryWithPassThrough(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis中查询店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断缓存是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.存在,返回店铺信息
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //(不存在,shopJson为null或者空字符串)判断命中的是否为空值
//        if (shopJson != null) {
//            //shopJson为空字符串时,返回错误信息
//            return null;
//        }
//        //4.shopJson为null时,根据id查询数据库
//        Shop shop = getById(id);
//        //5.判断店铺是否存在
//        if (shop == null) {
//            //6.不存在,将空值写入redis
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            //返回错误信息
//            return null;
//        }
//        //7.存在,将店铺信息存入redis中(添加随机时间防止缓存雪崩)
//        stringRedisTemplate.opsForValue()
//                .set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + RandomUtil.randomInt(5), TimeUnit.MINUTES);
//        //8.返回
//        return shop;
//    }
//
//    /*
//     * 获取锁
//     * */
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    /*
//     * 释放锁
//     * */
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }
    /*
     * 缓存重建
     * */
    public void saveShop2Redis(Long id, Long exipreSeconds) throws InterruptedException {
        //1.根据id查询数据库
        Shop shop = getById(id);
        Thread.sleep(200); //模拟延迟
        //2.设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);  //传入查询到的数据库信息
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(exipreSeconds)); //设置逻辑过期时间
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));//JSONUtil.toJsonStr():可以将任意字符串转换为json字符串
    }

    /*
     * 更新店铺
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId(); //获取到店铺id
        if (id == null) {  //判断店铺id是否存在
            //不存在,返回错误信息
            return Result.fail("店铺id不存在!");
        }

        //1.存在,更新店铺信息
        updateById(shop);
        //2.删除店铺缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /*
     * 附近商户
     * */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }

}
