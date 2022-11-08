package com.hmdp.service.impl;

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

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = "cache:shop:type";

        // 1.从 redis 中查询数据
        String typeJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if (typeJson != null){
            // 3.存在 直接返回
            List<ShopType> types = JSONUtil.toList(typeJson, ShopType.class);
            return Result.ok(types);
        }

        // 4.不存在，查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();

        // 5.数据库不存在，返回错误
        if (list == null) {
            return Result.fail("分类不存在");
        }

        // 6.存入 redis 中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(list), 30L, TimeUnit.MINUTES);

        // 7.返回
        return Result.ok(list);
    }
}
