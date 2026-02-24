package com.xin.picturebackend.api;

import com.xin.picturebackend.manager.review.ReviewManager;
import com.xin.picturebackend.manager.review.modle.AIReviewResultEnum;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @author 黄兴鑫
 * @since 2025/7/8 13:41
 */
@SpringBootTest
@Disabled("依赖固定测试数据和外部审核服务，默认环境不稳定")
public class ReviewAPITest {
    @Resource
    private ReviewManager reviewManager;

    @Test
    void test() {
        AIReviewResultEnum aiReviewResultEnum = reviewManager.syncAIReview(1942460631055486977L);
        System.out.println(aiReviewResultEnum);
    }
}
