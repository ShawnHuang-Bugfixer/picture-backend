package com.xin.picturebackend.manager.review;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ciModel.auditing.ImageAuditingRequest;
import com.qcloud.cos.model.ciModel.auditing.ImageAuditingResponse;
import com.qcloud.cos.model.ciModel.auditing.PornInfo;
import com.xin.picturebackend.manager.review.modle.AIReviewResultEnum;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.service.PictureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.xin.picturebackend.utils.COSKeyUtils.cosKeyHandler;
import static com.xin.picturebackend.utils.COSKeyUtils.cosOriginKeyHandler;

/**
 * 整合第三方审核接口，详见腾讯云对象存储图片审核。
 *
 * @author 黄兴鑫
 * @since 2025/7/8 14:10
 */
@Component
public class ReviewManager {
    private static final String BIZ_TYPE = "0cc163215bbe11f0a07c525400b75156"; // 存储桶审核策略

    @Value("${cos.client.bucket}")
    private String bucketName;

    @Value("${cos.client.host}")
    private String cosClientHost;

    @Resource
    private PictureService pictureService;

    @Resource
    private COSClient cosClient;

    private ImageAuditingRequest buildAuditingRequest(String cosObjKey) {
        //1.创建任务请求对象
        ImageAuditingRequest request = new ImageAuditingRequest();
        //2.添加请求参数 参数详情请见 API 接口文档
        //2.1设置请求 bucket
        request.setBucketName(bucketName);
        //2.2设置审核策略 不传则为默认策略（预设）
        request.setBizType(BIZ_TYPE);
        //2.3设置 bucket 中的图片位置
        request.setObjectKey("/space/1906728908816793601/2025-07-08_geuyaj65xvmv6pd8.webp");
        return request;
    }

    public AIReviewResultEnum syncAIReview(Long picId) {
        // 1. 构建图片审核请求
        Picture picture = pictureService.getById(picId);
        String webpKey = cosKeyHandler(picture.getUrl(), cosClientHost);
        String thumbnailKey = cosKeyHandler(picture.getThumbnailUrl(), cosClientHost);
        String originKey = cosOriginKeyHandler(webpKey, thumbnailKey);
        ImageAuditingRequest request = buildAuditingRequest(originKey);

        // 2. 同步执行图片审核
        ImageAuditingResponse auditingResponse = cosClient.imageAuditing(request);
        PornInfo pornInfo = auditingResponse.getPornInfo(); // 还可以配置其他内容，当且仅当所有审核内容都通过时判定为 AI 审核通过。
        String hitFlag = pornInfo.getHitFlag();
        return switch (hitFlag) {
            case "0" -> AIReviewResultEnum.AI_PASS;
            case "2" -> AIReviewResultEnum.AI_SUSPICIOUS;
            default -> AIReviewResultEnum.AI_REJECT;
        };
    }
}
