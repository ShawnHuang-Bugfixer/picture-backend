package com.xin.picturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.apiintegration.aliyunai.model.AliYunAiApi;
import com.xin.picturebackend.apiintegration.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xin.picturebackend.apiintegration.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xin.picturebackend.common.DeleteRequest;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.manager.CosManager;
import com.xin.picturebackend.manager.upload.FilePictureUpload;
import com.xin.picturebackend.manager.upload.URLPictureUpload;
import com.xin.picturebackend.mapper.ImagesearchhistoryMapper;
import com.xin.picturebackend.model.dto.file.UploadPictureResult;
import com.xin.picturebackend.model.dto.picture.*;
import com.xin.picturebackend.model.entity.Imagesearchhistory;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.PictureReviewStatusEnum;
import com.xin.picturebackend.model.vo.PictureVO;
import com.xin.picturebackend.model.vo.UserVO;
import com.xin.picturebackend.service.ImagesearchhistoryService;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.mapper.PictureMapper;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.service.UserService;
import com.xin.picturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Lenovo
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-02-27 16:22:08
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {
    @Resource
    private URLPictureUpload urlPictureUploader;

    @Resource
    private FilePictureUpload filePictureUploader;

    @Resource
    private UserService userService;

    @Resource
    private ImagesearchhistoryMapper historyMapper;

    @Resource
    private ImagesearchhistoryService historyService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RBloomFilter<String> pictureBloomFilter;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Override
    public PictureVO uploadPicture(Object resourceSource, PictureUploadRequest pictureUploadRequest, User user) {
        // 1. 图片更新请求和登录用户不能为 null
        ThrowUtils.throwIf(user == null || pictureUploadRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = null;
        String picName = null;
        pictureId = pictureUploadRequest.getId();
        picName = pictureUploadRequest.getPicName();
        // 2. 更新图片逻辑.数据库存在该图片 id. 更新权限校验。
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(user.getId()) && !userService.isAdmin(user)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 删除 cos 旧文件
            clearPictureFile(oldPicture);
            // 校验空间权限
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) spaceId = oldPicture.getSpaceId();
            } else {
                ThrowUtils.throwIf(!ObjUtil.equals(spaceId, oldPicture.getSpaceId()), ErrorCode.NO_AUTH_ERROR);
            }
        }
        // 3. 上传图片逻辑
        String uploadPrefix = String.format("public/%s", user.getId());
        // 3.1 判断是否指定上传到私有图库
        if (spaceId != null) {
            // 校验用户是否有权限操作该私有空间
            Space dbSpace = spaceService.getById(spaceId);
            ThrowUtils.throwIf(dbSpace == null, ErrorCode.PARAMS_ERROR, "space 不存在！");
            ThrowUtils.throwIf(!dbSpace.getUserId().equals(user.getId()), ErrorCode.NO_AUTH_ERROR);
            // 空间额度校验
            if (dbSpace.getTotalCount() >= dbSpace.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (dbSpace.getTotalSize() >= dbSpace.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间容量不足");
            }
            uploadPrefix = String.format("space/%s", spaceId);
        }
        UploadPictureResult uploadPictureResult = null;
        // 判断使用文件上传还是url上传
        if (resourceSource instanceof MultipartFile file) {
            uploadPictureResult = filePictureUploader.uploadPicture(file, uploadPrefix);
        } else {
            String resourceUrl = (String) resourceSource;
            uploadPictureResult = urlPictureUploader.uploadPicture(resourceUrl, uploadPrefix);
        }
        Picture picture = getPicture(picName, user, uploadPictureResult, pictureId);
        picture.setSpaceId(spaceId);
        picture.setPicColor(uploadPictureResult.getPicColor());
        // 重置审核状态
        fillPicture(picture, user);
        // 4. 保存到数据库
        // 开启事务
        Long finalSpaceId = spaceId;
        Boolean executeResult = transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        if (executeResult != null && executeResult) pictureBloomFilter.add(picture.getId().toString());
        return PictureVO.objToVo(picture);
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        // 参数校验
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) return queryWrapper;
        // 获取查询条件
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(wrapper -> wrapper.like("name", searchText)
                    .or()
                    .like("introduction", searchText));
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.between(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime, endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage) {
        List<Picture> records = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (records == null || records.isEmpty()) return pictureVOPage;
        Set<Long> userIds = records.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        List<PictureVO> pictureVOList = records.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userMap.containsKey(userId)) {
                user = userMap.get(userId);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 参数校验
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        ThrowUtils.throwIf(id == null || reviewStatus == null
                        || PictureReviewStatusEnum.REVIEWING.equals(PictureReviewStatusEnum.getEnumByValue(reviewStatus))
                , ErrorCode.PARAMS_ERROR, "审核状态不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 2. 数据库校验
        Picture picture = this.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 3. 审核状态校验
        ThrowUtils.throwIf(picture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR, "请勿重复校验");
        // 4. 更新审核状态
        Picture newPicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, newPicture);
        newPicture.setReviewerId(loginUser.getId());
        newPicture.setReviewStatus(reviewStatus);
        newPicture.setEditTime(new Date());
        boolean result = this.updateById(newPicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "审核失败");
    }

    @Override
    public void fillPicture(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    // fixme 重构，接入第三方 api，获取图片 url 集合
    @Override
    public int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 1. 参数校验
        Integer paraCount = pictureUploadByBatchRequest.getCount(); // 最大拉取数量
        String searchText = pictureUploadByBatchRequest.getSearchText(); // 图片关键词
        ThrowUtils.throwIf(paraCount == null || StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR, "参数错误");
        int successUploadCount = 0;
        int first = applicationContext.getBean(PictureServiceImpl.class).getScrollingPage(searchText); //拿到动态代理对象后才能激活 @Transactional
        // 3. 利用 first 和 count 构造 url，从 bing 搜索引擎中获取图片 url 集合
        String fetchURL = String.format("https://cn.bing.com/images/async?q=%s&first=%s&count=%s&mmasync=1", searchText, first, "35");
        Document document = null;
        try {
            document = Jsoup.connect(fetchURL).get();
            Element div = document.getElementsByClass("dgControl").first();
            ThrowUtils.throwIf(ObjUtil.isNull(div), ErrorCode.OPERATION_ERROR, "jsoup 获取元素失败");
            Elements imgElementList = div.select("img.mimg");
            // 4. 遍历集合，上传到 COS 对象存储和数据库，记录成功上传的数量
            for (Element imgElement : imgElementList) {
                String url = imgElement.attr("src");
                if (StrUtil.isBlank(url)) {
                    log.info("当前元素 URL 为空 URL = {}", url);
                    continue;
                }
                // url 处理
                int questionMarkIndex = url.indexOf("?");
                url = questionMarkIndex > -1 ? url.substring(0, questionMarkIndex) : url;
                // 上传图片,批量上传时默认名称前缀为关键字
                String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
                if (StrUtil.isBlank(namePrefix)) {
                    namePrefix = searchText;
                }
                PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
                String uuid = UUID.randomUUID().toString().replace("-", "");
                pictureUploadRequest.setPicName(namePrefix + uuid.substring(0, 8));
                PictureVO pictureVO = null;
                try {
                    pictureVO = uploadPicture(url, pictureUploadRequest, loginUser);
                } catch (BusinessException e) {
                    log.info("图片上传失败, url = {}, 原因 = {}", url, e.getMessage());
                }
                if (pictureVO != null) successUploadCount++;
                if (successUploadCount >= paraCount) {
                    break;
                }
                Thread.sleep((int) (Math.random() * 1000));
            }
        } catch (RuntimeException e) {
            log.error("网络错误，请稍后重试", e);
        } catch (Exception e) {
            log.error("");
        }
        return successUploadCount;
    }

    @Override
    @CacheEvict(cacheManager = "multiLevelCacheManger", value = "pictureHotKey", key = "'picture:pictureVO:' + #pictureUpdateRequest.id")
    public void updatePicture(PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 设置审核状态
        fillPicture(picture, userService.getLoginUser(request));
        // 操作数据库
        boolean result = updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void deletePicture(DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Picture oldPicture = getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(id);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
// 异步清理文件
        this.clearPictureFile(oldPicture);

        // 删除 cos
        clearPictureFile(oldPicture);
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        checkPictureAuth(loginUser, oldPicture);
        // 设置审核状态
        fillPicture(picture, userService.getLoginUser(request));
        // 操作数据库
        boolean result = updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 热门页面：缓存前N页（如1-10页），TTL较短（如1-5分钟）。
     * 冷门页面：按需缓存或设置较长TTL（如10分钟），结合LRU淘汰策略。
     *
     * @param pictureQueryRequest 图片查询
     * @return 返回图片视图集合
     */
    @Override
    @Caching(
            cacheable = {
                    @Cacheable(cacheManager = "redisCacheManager",
                            value = "pictureHotVOList",
                            key = "'picture:pictureVOList:' + "
                                    + "T(org.springframework.util.DigestUtils).md5DigestAsHex("
                                    + "T(cn.hutool.json.JSONUtil).toJsonStr(#a0).getBytes())",
                            condition = "#a0.current >= 1 && #a0.current <= 20 && #a0.spaceId == null"  // 使用 #a0
                    ),
                    @Cacheable(cacheManager = "redisCacheManager",
                            value = "pictureColdVOList",
                            key = "'picture:pictureVOList:' + "
                                    + "T(org.springframework.util.DigestUtils).md5DigestAsHex("
                                    + "T(cn.hutool.json.JSONUtil).toJsonStr(#a0).getBytes())",
                            condition = "#a0.current >= 21 && #a0.current <= 100 &&a0.spaceId == null"
                    )
            }
    )
    public Page<PictureVO> listPictureVoByPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        Long spaceId = pictureQueryRequest.getSpaceId();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 公共图库
        if (spaceId == null) {
            // 普通用户只能查看已经通过审核的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            Space dbSpace = spaceService.getById(spaceId);
            if (dbSpace == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间不存在");
            User loginUser = userService.getLoginUser(request);
            if (!ObjUtil.equals(loginUser.getId(), dbSpace.getUserId()))
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限访问该空间");
        }
        // 查询数据库
        // 使用分布式锁替代synchronized
        RLock lock = redissonClient.getLock("picture:PAGE_LOCK:" + current);
        try {
            if (lock.tryLock(1, 10, TimeUnit.SECONDS)) {
                Page<Picture> picturePage = this.page(new Page<>(current, size),
                        this.getQueryWrapper(pictureQueryRequest));
                return getPictureVOPage(picturePage);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        } finally {
            lock.unlock();
        }
        return new Page<>();
    }

    @Async
    @Override
    public void clearPictureFile(Picture picture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = picture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        String webpKey = cosKeyHandler(picture.getUrl());
        String thumbnailKey = cosKeyHandler(picture.getThumbnailUrl());
        String originKey = cosOriginKeyHandler(webpKey, thumbnailKey);
        cosManager.deleteObject(webpKey);
        cosManager.deleteObject(originKey);
        cosManager.deleteObject(thumbnailKey);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 计算图片相似度并排序
        List<Picture> spacePicList = this.lambdaQuery().eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(spacePicList)) {
            return Collections.emptyList();
        }
        List<PictureVO> sortedResult = spacePicList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String rgb = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(rgb)) {
                        return Double.MAX_VALUE;
                    }
                    return 1 - ColorSimilarUtils.getImageSimilarity(rgb, picColor);
                }))
                .limit(12)
                .map(PictureVO::objToVo)
                .toList();
        return sortedResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询数据，写回更新数据
        List<Picture> dbList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        dbList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureNameWithNameRule(dbList, nameRule);
        boolean b = this.updateBatchById(dbList);
        if (!b) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "批量修改图片信息失败");
        }
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 权限校验
        checkPictureAuth(loginUser, picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }


    /**
     * nameRule 格式为：图片{序号}
     */
    private void fillPictureNameWithNameRule(List<Picture> dbList, String nameRule) {
        if (CollUtil.isEmpty(dbList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : dbList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    private static String cosOriginKeyHandler(String webpKey, String thumbnailKey) {
        int i = thumbnailKey.indexOf(".");
        String fileExtension = thumbnailKey.substring(i);
        String keyPrefix = webpKey.substring(0, webpKey.length() - 5);
        return keyPrefix + fileExtension;
    }

    private static String cosKeyHandler(String url) {
        String COS_PREFIX_URL = "https://huang-picture-1345225028.cos.ap-chengdu.myqcloud.com";
        ThrowUtils.throwIf(!url.contains(COS_PREFIX_URL), ErrorCode.PARAMS_ERROR, "cosKey 处理错误，url 错误");
        int i = url.indexOf(COS_PREFIX_URL);
        return url.substring(i + COS_PREFIX_URL.length());
    }

    /**
     * 查询批量拉取历史表，返回指定关键词滑动分页的起始页码。
     * 利用 keyword，查询数据库，如果不存在，first = 1 count = 35。
     * 如果存在，从数据库中获取 first 和 count first += count。将结果写回数据库
     *
     * @param keyword 批量拉取图片的关键词
     * @return 返回 keyword 对应的滑动分页起始页码
     */
    @Transactional
    public int getScrollingPage(String keyword) {
        int first = 1;
        int count = 35;
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            QueryWrapper<Imagesearchhistory> historyQueryWrapper = new QueryWrapper<>();
            historyQueryWrapper.eq("searchKeyword", keyword);
            Imagesearchhistory selectedOne = historyMapper.selectOne(historyQueryWrapper);
            if (selectedOne != null) {
                count = selectedOne.getCount();
                first = selectedOne.getFirst() + count;
            }
            selectedOne = ObjUtil.isNull(selectedOne) ? new Imagesearchhistory() : selectedOne;
            selectedOne.setCount(count);
            selectedOne.setFirst(first);
            selectedOne.setUpdatedAt(new Date());
            selectedOne.setSearchKeyword(keyword);
            boolean success = historyService.saveOrUpdate(selectedOne); // 乐观锁
            if (!success) {
                retryCount++;
                continue;
            } else {
                return first;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取滑动分页页码失败");
    }

    /**
     * 将数据万象返回的图片信息封装成 Picture 对象
     *
     * @param picName             批量抓取时，管理员设置的别名
     * @param user                当前登录用户
     * @param uploadPictureResult 数据万象返回的图片信息
     * @param pictureId           图片id
     * @return 返回 Picture 对象
     */
    private static Picture getPicture(String picName, User user, UploadPictureResult uploadPictureResult, Long pictureId) {
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        if (StrUtil.isNotBlank(picName)) {
            picture.setName(picName);
        } else {
            picture.setName(uploadPictureResult.getPicName());
        }
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(user.getId());
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        return picture;
    }

    /**
     * cache-aside 多级缓存架构。同时实现 hotkey 监控。
     *
     * @param id picture id
     * @return 返回去敏后数据 pictureVO
     */
    @Override
    @Cacheable(cacheManager = "multiLevelCacheManger", value = "pictureHotKey", key = "'picture:pictureVO:' + #id", sync = true)
    public PictureVO getPictureVOById(long id, HttpServletRequest request) {
        log.error("缓存失效！");
        if (!pictureBloomFilter.contains(String.valueOf(id))) {
            return null;
        }
        Picture picture = this.getById(id);
        if (picture == null) {
            return null;
        }
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            User loginUser = userService.getLoginUser(request);
            checkPictureAuth(loginUser, picture);
        }
        return getPictureVO(picture);
    }
}




