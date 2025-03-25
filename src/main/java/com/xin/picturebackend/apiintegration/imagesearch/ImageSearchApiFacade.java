package com.xin.picturebackend.apiintegration.imagesearch;

import com.xin.picturebackend.apiintegration.imagesearch.model.ImageSearchResult;

import java.util.List;

import static com.xin.picturebackend.apiintegration.imagesearch.sub.GetImagePageUrlApi.getImagePageUrl;
import static com.xin.picturebackend.apiintegration.imagesearch.sub.GetSimilarPictureListApi.getSimilarPictureList;
import static com.xin.picturebackend.apiintegration.imagesearch.sub.GetSimilarPictureListUrlApi.getSimilarPictureListUrl;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/24 11:52
 */
public class ImageSearchApiFacade {
    /**
     * 搜索图片
     *
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = getImagePageUrl(imageUrl);
        String similarPictureListUrl = getSimilarPictureListUrl(imagePageUrl);
//        System.out.println("相似图片列表 URL: " + similarPictureListUrl);
        List<ImageSearchResult> similarPictureList = getSimilarPictureList(similarPictureListUrl);
//        similarPictureList.forEach(System.out::println);
        return similarPictureList;
    }

    public static void main(String[] args) {
        searchImage("http://mms2.baidu.com/it/u=2271139727,3863662922&fm=253&app=120&f=JPEG?w=608&h=342");
    }
}
