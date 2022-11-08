package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存穿透 解决方案，设置 null 值
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        // 逻辑过期解决缓存击穿
        // Shop shop = queryWithLogicalExpire(id);
        // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    /**
     * 逻辑过期 解决缓存击穿问题
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从 redis 中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3.不存在 返回空
            return null;
        }

        // 4.命中 需要把 json 反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return shop;
        }
        // 5.2.已过期，需要缓存重建

        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock) {
            // 获取锁成功一个再次检测 redis 缓存是否过期，做双重检查，如果存在则无需重建缓存
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 5.1.未过期，直接返回店铺信息 释放锁
                unlock(lockKey);
                return shop;
            }
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 缓存重建
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4.失败，返回过期的商铺信息
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1.从 redis 中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在 直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的是否是空值
        if (shopJson != null) {
            return null;
        }

        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = null;
        Shop shop = null;
        try {
            lockKey = "lock:shop:" + id;
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!isLock){
                // 4.3.失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 4.4.成功，根据 id 查询数据库
            shop = getById(id);
            // 模拟重建延迟
            Thread.sleep(200);

            // 5.数据库不存在，返回错误
            if (shop == null) {
                // 将空值写入 redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6.数据库存在，写入 redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放互斥锁
            unlock(lockKey);
        }

        // 8.返回
        return shop;
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从 redis 中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在 直接返回
            return JSONUtil.toBean(shopJson, Shop.class);

        }

        // 判断命中的是否是空值
        if (shopJson != null) {
            return null;
        }

        // 4.不存在，根据 id 查询数据库
        Shop shop = getById(id);

        // 5.数据库不存在，返回错误
        if (shop == null) {
            // 缓存穿透 将空值写入 redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6.数据库存在，写入 redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7.返回
        return shop;
    }

    // 获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入 redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
