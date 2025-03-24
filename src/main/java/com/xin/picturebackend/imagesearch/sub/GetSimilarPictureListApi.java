package com.xin.picturebackend.imagesearch.sub;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.imagesearch.model.ImageSearchResult;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/24 11:23
 */
@Slf4j
public class GetSimilarPictureListApi {
    /**
     * 从  firstUrl 中解析 json，获取相似图片的 URL
     * @param url firstUrl
     * @return List<ImageSearchResult>
     */
    public static List<ImageSearchResult> getSimilarPictureList(String url) {
        List<ImageSearchResult> resultList = new ArrayList<>();
        try {
            // 创建URL对象
            URL apiUrl = new URL(url);
            // 打开HTTP连接
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");

            // 读取响应
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // 解析JSON
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.toString());

            // 获取"list"数组
            JsonNode listNode = rootNode.path("data").path("list");
            for (JsonNode item : listNode) {
                ImageSearchResult result = new ImageSearchResult();
                result.setThumbUrl(item.path("thumbUrl").asText());
                result.setFromUrl(item.path("fromUrl").asText());
                resultList.add(result);
            }
        } catch (Exception e) {
            log.error("请求 {} 时获取 json 数据失败", url);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求相似图片集合 url 失败");
        }
        return resultList;
    }
}
