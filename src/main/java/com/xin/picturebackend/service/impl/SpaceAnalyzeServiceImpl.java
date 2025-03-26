package com.xin.picturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.space.*;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.vo.*;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.service.SpaceAnalyzeService;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 黄兴鑫
 * @since 2025/3/26 9:34
 */
@Service
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {
    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    /**
     * 校验空间分析权限
     *
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 检查权限
        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()) {
            // 全空间分析或者公共图库权限校验：仅管理员可访问
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权访问公共图库");
        } else {
            // 私有空间权限校验
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }

    /**
     * 根据查询的空间填充查询Wrapper
     *
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }

    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "空间分析请求为 null");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 2. 权限校验
        checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
        // 3. 分析全部或者公共空间信息
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
            pictureQueryWrapper.select("SUM(picSize) as usedSize, count(1) as usedCount");
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, pictureQueryWrapper);
            List<Map<String, Object>> maps = pictureService.getBaseMapper().selectMaps(pictureQueryWrapper);
            Map<String, Object> map = maps.get(0);
            Long usedSize = Long.parseLong(map.get("usedSize").toString());
            Long usedCount = Long.parseLong(map.get("usedCount").toString());
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        } else {
            // 4. 分析私人空间信息
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            Space dbSpace = spaceService.getById(spaceId);
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(dbSpace.getTotalSize());
            spaceUsageAnalyzeResponse.setMaxSize(dbSpace.getMaxSize());
            double sizeRatio = NumberUtil.round(dbSpace.getTotalSize() * 100.0 / dbSpace.getMaxSize(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeRatio);
            spaceUsageAnalyzeResponse.setUsedCount(dbSpace.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxCount(dbSpace.getMaxCount());
            double countRation = NumberUtil.round(dbSpace.getTotalCount() * 100.0 / dbSpace.getMaxCount(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setCountUsageRatio(countRation);
            return spaceUsageAnalyzeResponse;
        }
    }

    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "空间分析请求为 null");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 2. 权限校验
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);
        // 3. 分析全部,公共空间或者私人空间中图片类别信息
        SpaceCategoryAnalyzeResponse spaceCategoryAnalyzeResponse = new SpaceCategoryAnalyzeResponse();
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, pictureQueryWrapper);
        pictureQueryWrapper.select("category", "COUNT(*) AS count", "SUM(picSize) AS totalSize").groupBy("category");
        List<Map<String, Object>> maps = pictureService.getBaseMapper().selectMaps(pictureQueryWrapper);
        return maps.stream().map(resultMap -> {
            String category = resultMap.get("category") != null ? resultMap.get("category").toString() : "未分类";
            Long count = Long.parseLong(resultMap.get("count").toString());
            Long totalSize = Long.parseLong(resultMap.get("totalSize").toString());
            return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
        }).toList();
    }

    @Override
    public List<SpaceTagAnalyzeResponse>
    getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "空间分析请求为 null");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 2. 权限校验
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);
        // 3. 分析全部,公共空间或者私人空间中图片标签信息
        SpaceTagAnalyzeResponse spaceTagAnalyzeResponse = new SpaceTagAnalyzeResponse();
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, pictureQueryWrapper);
        pictureQueryWrapper.select("tags");
        // 3.1 获取图片标签 Json 列表
        List<String> tagsJsonList = pictureService.getBaseMapper()
                .selectObjs(pictureQueryWrapper)
                .stream()
                .filter(ObjectUtil::isNotNull)
                .map(Object::toString)
                .toList();
        // 3.2 将图片标签 Json 列表转换为图片标签列表并扁平化最后统计每个标签的出现次数
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
        // 3.3 以降序排序并返回
        return tagCountMap.entrySet().stream()
                .sorted((entry1, entry2) -> Long.compare(entry2.getValue(), entry1.getValue()))
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "空间分析请求为 null");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 2. 权限校验
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);
        // 3. 分析全部,公共空间或者私人空间中图片大小信息
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, pictureQueryWrapper);
        pictureQueryWrapper.select("picSize");
        List<Long> picSizes = pictureService.getBaseMapper()
                .selectObjs(pictureQueryWrapper)
                .stream()
                .filter(ObjectUtil::isNotNull)
                .map(Long.class::cast)
                .toList();
        // 4. 构造分段统计结果
        // 定义分段范围，注意使用有序 Map
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizes.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizes.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizes.stream().filter(size -> size >= 500 * 1024 && size < 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizes.stream().filter(size -> size >= 1024 * 1024).count());
        return sizeRanges.entrySet()
                .stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "空间分析请求为 null");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 2. 权限校验
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);
        // 3. 分析全部,公共空间或者私人空间中图片大小信息
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, pictureQueryWrapper);
        // 4. 根据不同时间维度查询
        switch (spaceUserAnalyzeRequest.getTimeDimension()) {
            case "day" -> {
                pictureQueryWrapper.select("DATE_FORMAT(editTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
            }
            case "week" -> {
                pictureQueryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
            }
            case "month" -> {
                pictureQueryWrapper.select("DATE_FORMAT(editTime, '%Y-%m') AS period", "COUNT(*) AS count");
            }
            default -> {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间维度错误");
            }
        }
        pictureQueryWrapper.groupBy("period").orderByAsc("period");
        Long userId = spaceUserAnalyzeRequest.getUserId();
        pictureQueryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        List<Map<String, Object>> maps = pictureService.getBaseMapper().selectMaps(pictureQueryWrapper);
        return maps.stream()
                .map(map -> {
                    String period = map.get("period").toString();
                    Long count = Long.valueOf(map.get("count").toString());
                    return new SpaceUserAnalyzeResponse(period, count);
                }).toList();
    }

    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "空间分析请求为 null");
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看");
        return spaceService.list(new QueryWrapper<Space>()
                .select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("limit " + spaceRankAnalyzeRequest.getTopN()));
    }
}
