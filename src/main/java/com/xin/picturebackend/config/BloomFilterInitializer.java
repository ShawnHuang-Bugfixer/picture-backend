package com.xin.picturebackend.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xin.picturebackend.mapper.PictureMapper;
import com.xin.picturebackend.model.entity.Picture;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;

/**
 * 初始化布隆过滤实例
 *
 * @author 黄兴鑫
 * @since 2025/3/15 9:51
 */
@Component
public class BloomFilterInitializer {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private PictureMapper pictureMapper;

    // 图片布隆过滤器
    private RBloomFilter<String> pictureBloomFilter;

    @PostConstruct
    public void initPictureBloomFilter() {
        // 图片场景的布隆过滤器
        pictureBloomFilter = redissonClient.getBloomFilter("pictureBloomFilter");
        pictureBloomFilter.tryInit(10000000L, 0.01);  // 图片预期10万数据，误判率1%
        List<Long> pictureIDs = pictureMapper.selectList(new QueryWrapper<>()).stream().map(Picture::getId).toList();
        pictureIDs.forEach((i) -> pictureBloomFilter.add(String.valueOf(i)));
    }

    // 图片过滤器Bean
    @Bean("pictureBloomFilter")
    public RBloomFilter<String> getPictureBloomFilter() {
        return pictureBloomFilter;
    }
}