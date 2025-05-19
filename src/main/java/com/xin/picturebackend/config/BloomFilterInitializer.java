package com.xin.picturebackend.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xin.picturebackend.mapper.PictureMapper;
import com.xin.picturebackend.model.entity.Picture;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@Configuration
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

        int batchSize = 1000; // 每批次处理的数据量
        long offset = 0; // 当前批次起始位置
        boolean hasMoreData = true;

        while (hasMoreData) {
            // 分批次查询数据
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.last("LIMIT " + offset + ", " + batchSize);
            List<Long> pictureIDs = pictureMapper.selectList(queryWrapper)
                    .stream()
                    .map(Picture::getId)
                    .toList();

            // 将当前批次的ID添加到布隆过滤器中
            pictureIDs.forEach(id -> pictureBloomFilter.add(String.valueOf(id)));

            // 判断是否还有更多数据
            if (pictureIDs.size() < batchSize) {
                hasMoreData = false;
            } else {
                offset += batchSize; // 更新偏移量，准备查询下一批次
            }
        }
    }

    // 图片过滤器Bean
    @Bean("pictureBloomFilter")
    public RBloomFilter<String> getPictureBloomFilter() {
        return pictureBloomFilter;
    }
}