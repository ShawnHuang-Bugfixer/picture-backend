package com.xin.picturebackend.service;


import com.xin.picturebackend.mapper.PictureMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;


import static org.junit.jupiter.api.Assertions.*;

/**
 * 布隆过滤测试
 *
 * @author 黄兴鑫
 * @since 2025/3/15 10:04
 */
@SpringBootTest
public class BloomServiceTest {

    @Resource
    private RBloomFilter<String> pictureBloomFilter;

    @Resource
    private PictureMapper pictureMapper;

    // 测试添加后能正确判断存在
    @Test
    public void testAddedDataExists() {
        System.out.println(pictureBloomFilter.contains("1895080388535922690"));
    }

    // 测试未添加的数据应返回不存在（允许极低概率误判）
    @Test
    public void testNonAddedDataNotExist() {
        String nonExistingData = "non_existing_789";
        assertFalse(pictureBloomFilter.contains(nonExistingData));
    }

    // 验证误判率是否符合预期（统计测试）
    @Test
    public void testFalsePositiveRate() {
        // 添加一批测试数据
        for (int i = 0; i < 10000; i++) {
            pictureBloomFilter.add("key_" + i);
        }

        // 检查未添加的数据的误判数量
        int falsePositives = 0;
        int testCases = 10000;
        for (int i = 10000; i < 20000; i++) {
            if (pictureBloomFilter.contains("key_" + i)) {
                falsePositives++;
            }
        }

        double actualRate = falsePositives / (double) testCases;
        System.out.println("实际误判率: " + actualRate);

        // 允许实际误判率 <= 初始化时的理论值 (0.03) + 误差容忍
        assertTrue(actualRate <= 0.03 + 0.005, "误判率超出预期");
    }

    // 清理布隆过滤器（避免影响其他测试）
//    @AfterEach
    public void tearDown() {
        pictureBloomFilter.delete(); // 删除Redis中的布隆过滤器
    }
}
