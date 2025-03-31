package com.xin.picturebackend.apiintegration.com.pixabay.api;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量搜索图片api
 *
 * @author 黄兴鑫
 * @since 2025/3/30 22:45
 */
@Component
@Slf4j
public class SearchPicturesAPI {
    private static final String baseUrl = "https://pixabay.com/api/";

    @Value("${pixabay.apiKey:}")
    private String key;

    private String getSearchPicturesUrl(String keyword, int page, int perPage, String lang) {
        return baseUrl + "?key=" + key + "&q=" + keyword + "&page=" + page + "&per_page=" + perPage + "&lang=" + lang;
    }

    public List<String> searchPicturesUrls(String keyword, int page, int perPage, String lang) {
        String fetchUrl = getSearchPicturesUrl(keyword, page, perPage, lang);
        JSONArray hitsArray = null;

        try {
            // 1. 发起 GET 请求获取 JSON 数据
            String jsonResponse = HttpUtil.get(fetchUrl);

            // 2. 解析 JSON
            JSONObject jsonObject = JSONUtil.parseObj(jsonResponse);
            hitsArray = jsonObject.getJSONArray("hits");
        } catch (Exception e) {
            log.error("请求 {} 时获取 json 数据失败", fetchUrl);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量操作失败");
        }

        // 3. 提取 webformatURL 并存入 List
        List<String> imageUrls = new ArrayList<>();
        for (int i = 0; i < hitsArray.size(); i++) {
            JSONObject hit = hitsArray.getJSONObject(i);
            String webformatUrl = hit.getStr("webformatURL");
            imageUrls.add(webformatUrl);
        }

        return imageUrls;
    }
}
