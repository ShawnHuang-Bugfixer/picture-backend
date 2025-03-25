package com.xin.picturebackend.apiintegration.imagesearch.sub;

import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/24 11:23
 */
@Slf4j
public class GetSimilarPictureListUrlApi {
    /**
     * 请求页面地址，利用 Jsoup 获取 firstUrl，该 url 中包含相似图片的 json 数据。
     * @param url 页面地址
     * @return firstUrl
     */
    public static String getSimilarPictureListUrl(String url) {
        // 获取网页内容
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            log.error("请求 firstUrl : {} 失败", url);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求 firstUrl 失败");
        }
        // 获取所有的 <script> 标签
        Elements scripts = doc.select("script");

        // 正则表达式匹配 firstUrl
        Pattern pattern = Pattern.compile("\\\"firstUrl\\\":\\\"(.*?)\\\"");

        for (Element script : scripts) {
            String scriptContent = script.html();
            Matcher matcher = pattern.matcher(scriptContent);
            if (matcher.find()) {
                // 解析到 firstUrl
                return matcher.group(1).replace("\\", "");
            }
        }

        return null; // 没找到相似图片的 URL
    }

}
