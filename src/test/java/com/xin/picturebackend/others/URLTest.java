package com.xin.picturebackend.others;


import com.xin.picturebackend.apiintegration.imagesearch.model.ImageSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.xin.picturebackend.apiintegration.imagesearch.sub.GetImagePageUrlApi.getImagePageUrl;
import static com.xin.picturebackend.apiintegration.imagesearch.sub.GetSimilarPictureListApi.getSimilarPictureList;
import static com.xin.picturebackend.apiintegration.imagesearch.sub.GetSimilarPictureListUrlApi.getSimilarPictureListUrl;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/23 11:19
 */

@Slf4j
public class URLTest {

    @Test
    void test() {
        String imagePageUrl = getImagePageUrl("http://mms2.baidu.com/it/u=2271139727,3863662922&fm=253&app=120&f=JPEG?w=608&h=342");
        String similarPictureListUrl = getSimilarPictureListUrl(imagePageUrl);
        System.out.println("相似图片列表 URL: " + similarPictureListUrl);
        List<ImageSearchResult> similarPictureList = getSimilarPictureList(similarPictureListUrl);
        similarPictureList.forEach(System.out::println);
    }
}
