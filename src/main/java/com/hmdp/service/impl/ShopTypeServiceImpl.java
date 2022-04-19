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

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
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
    public Result queryShopTypeList() {

        // 在Redis中查询
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);
        // 判断是否存在
        if (!shopTypeList.isEmpty()) {
            List<ShopType> shopTypeListBean = new ArrayList<>();
            for (String s : shopTypeList) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                shopTypeListBean.add(shopType);
            }
            return Result.ok(shopTypeListBean);
        }
        // 不存在，查询数据库
        List<ShopType> shopTypeListBean = query().orderByAsc("sort").list();

        if (shopTypeListBean.isEmpty()) {
            // 数据库中不存在，返回错误
            return Result.fail("不存在的店铺类型");
        }
        // 数据库中存在，添加进Redis
        for (ShopType shopType : shopTypeListBean) {
            String s = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(s);
        }
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOPTYPE_KEY, shopTypeList);
        // 返回列表
        return Result.ok(shopTypeListBean);
    }
}
