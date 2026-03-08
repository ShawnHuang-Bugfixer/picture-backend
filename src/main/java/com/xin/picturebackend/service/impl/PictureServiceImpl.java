package com.xin.picturebackend.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.apiintegration.aliyunai.model.AliYunAiApi;
import com.xin.picturebackend.apiintegration.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xin.picturebackend.apiintegration.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xin.picturebackend.apiintegration.com.pixabay.api.SearchPicturesAPI;
import com.xin.picturebackend.auth.AuthManager;
import com.xin.picturebackend.auth.constant.PermissionConstants;
import com.xin.picturebackend.common.DeleteRequest;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
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
import com.xin.picturebackend.model.enums.SpaceTypeEnum;
import com.xin.picturebackend.model.messagequeue.review.ReviewContentMessage;
import com.xin.picturebackend.model.vo.PictureVO;
import com.xin.picturebackend.model.vo.UserVO;
import com.xin.picturebackend.service.*;
import com.xin.picturebackend.mapper.PictureMapper;
import com.xin.picturebackend.service.statemachine.context.ContextKey;
import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import com.xin.picturebackend.utils.ColorSimilarUtils;
import com.xin.picturebackend.utils.StateMachineUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.xin.picturebackend.utils.COSKeyUtils.*;

/**
 * @author Lenovo
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-02-27 16:22:08
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {
    private static final String MEDIA_TYPE_IMAGE = "image";
    private static final String MEDIA_TYPE_VIDEO = "video";
    private static final long SINGLE_VIDEO_MAX_SIZE = 200 * 1024 * 1024L;
    private static final Set<String> ALLOW_VIDEO_FORMAT_LIST = Set.of("mp4", "mov", "mkv", "avi", "webm", "m4v");

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

    @Resource
    private AuthManager authManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SearchPicturesAPI searchPicturesAPI;

    @Value("${cos.client.host}")
    private String cosClientHost;

    @Resource
    private AmqpTemplate amqpTemplate;

    @Resource
    @Lazy
    private StateMachineFactory<ImageReviewState, ImageReviewEvent> stateMachineFactory;

    @Override
    public PictureVO uploadPicture(Object resourceSource, PictureUploadRequest pictureUploadRequest, User user) {
        ThrowUtils.throwIf(ObjUtil.hasNull(resourceSource, pictureUploadRequest, user), ErrorCode.PARAMS_ERROR);
        String mediaType = normalizeMediaType(pictureUploadRequest.getMediaType());
        Long id = pictureUploadRequest.getId();
        String picName = pictureUploadRequest.getPicName();
        Long spaceId = pictureUploadRequest.getSpaceId();
        Picture oldDbPicture = null;
        // 1. id == null 上传逻辑 权限校验
        if (ObjectUtil.isNull(id)) {
            // 1.1 spaceId == null 上传到公共空间
            if (ObjectUtil.isNull(spaceId)) {
                StpUtil.checkPermission(PermissionConstants.PUBLIC_UPLOAD_IMAGE);
            } else {
                // 1.2 spaceId ！= null 上传到非公共空间
                Space dbSpace = spaceService.getById(spaceId);
                ThrowUtils.throwIf(ObjUtil.isNull(dbSpace), ErrorCode.PARAMS_ERROR, "space 不存在");
                if (dbSpace.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
                    // 1.2.2 spaceType == Private 校验是否有上传私有空间权限
                    // 私有空间，仅私有空间拥有者可操作
                    StpUtil.checkPermission(PermissionConstants.PRIVATE_UPLOAD_IMAGE);
                }
                if (dbSpace.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                    // 1.2.1 spaceType == Team 校验是否有团队上传图片权限
                    // 团队空间，要求有编辑权限
                    StpUtil.checkPermission(PermissionConstants.TEAM_UPLOAD_IMAGE);
                }
            }
        } else {
            // 2. id ！= null 更新逻辑权限校验
            //    从数据库中查询图片信息获取 spaceId
            oldDbPicture = this.getById(id);
            ThrowUtils.throwIf(ObjUtil.isNull(oldDbPicture), ErrorCode.PARAMS_ERROR, "图片不存在");
            spaceId = oldDbPicture.getSpaceId();
            if (ObjectUtil.isNull(spaceId)) {
                // 2.1 spaceId == null 更新公共空间图片
                StpUtil.checkPermission(PermissionConstants.PUBLIC_MODIFY_IMAGE);
            } else {
                // 2.2 spaceId ！= null 更新到非公共空间
                Space dbSpace = spaceService.getById(spaceId);
                ThrowUtils.throwIf(ObjUtil.isNull(dbSpace), ErrorCode.PARAMS_ERROR, "space 不存在");
                if (dbSpace.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
                    // 2.2.2 spaceType == Private 校验是否有更新私有空间图片权限
                    // 私有空间，仅私有空间拥有者可操作
                    StpUtil.checkPermission(PermissionConstants.PRIVATE_MODIFY_IMAGE);
                }
                if (dbSpace.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                    // 2.2.1 spaceType == Team 校验是否有更新团队图片权限
                    // 团队空间，要求有编辑权限
                    StpUtil.checkPermission(PermissionConstants.TEAM_MODIFY_IMAGE);
                }
            }
        }
        // 3. spaceId ！= null 时空间容量大小校验并设置私有空间上传前缀
        String uploadPrefix = String.format("public/%s", user.getId()); // 公共空间上传前缀
        if (ObjectUtil.isNotNull(spaceId)) {
            uploadPrefix = String.format("space/%s", spaceId); // 非公共空间上传前缀
            Space dbSpace = spaceService.getById(spaceId);
            // 空间额度校验
            if (dbSpace.getTotalCount() >= dbSpace.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (dbSpace.getTotalSize() >= dbSpace.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间容量不足");
            }
        }
        // 4. 选择文件上传方式
        UploadPictureResult uploadPictureResult;
        if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
            ThrowUtils.throwIf(!(resourceSource instanceof MultipartFile), ErrorCode.PARAMS_ERROR, "视频仅支持文件上传");
            uploadPictureResult = uploadVideo((MultipartFile) resourceSource, uploadPrefix);
        } else {
            if (resourceSource instanceof MultipartFile file) {
                // 文件上传
                uploadPictureResult = filePictureUploader.uploadPicture(file, uploadPrefix);
            } else {
                // url 上传
                String resourceUrl = (String) resourceSource;
                uploadPictureResult = urlPictureUploader.uploadPicture(resourceUrl, uploadPrefix);
            }
        }
        // 5. 构造上传成功后的图片信息
        Picture picture = getPicture(picName, user, uploadPictureResult, id);
        picture.setSpaceId(spaceId);
        picture.setPicColor(uploadPictureResult.getPicColor());
        // 5.1 重置审核状态（视频默认过审，不进入图片审核链路）
        if (isVideoFormat(uploadPictureResult.getPicFormat())) {
            fillVideoReview(picture, user);
        } else {
            fillPicture(picture, user);
        }
        // 6. 开启事务，新增或更新图片信息同时更新空间额度
        final Long finalSpaceId = spaceId;
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
        // 更新完毕后，重新读取一次 picture 对象
        Picture fullPicture = this.getById(picture.getId());
        // 普通用户上传到公共空间且图片未过审才需要审核
        if (!userService.isAdmin(user)
                && spaceId == null
                && !isVideoFormat(fullPicture.getPicFormat())
                && fullPicture.getReviewStatus() != PictureReviewStatusEnum.FINAL_APPROVED.getValue())
            sentReviewContentToMessageQueue(picture);
        // 6.1 添加到布隆过滤器
        if (executeResult != null && executeResult) {
            pictureBloomFilter.add(picture.getId().toString());
            if (oldDbPicture != null) {
                // 6.2 如果是更新，删除 cos 旧图片文件
                clearPictureFile(oldDbPicture);
            }
        }
        // 8. cache-aside 模式，先更新数据库，再删除缓存
        if (ObjectUtil.isNotNull(id)) {
            stringRedisTemplate.delete(String.format("picture:pictureVO:%s", id));
        }
        // 7. 返回图片信息
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
        Integer reviewStatus = pictureReviewRequest.getReviewStatus(); // 前端传递，只有 6 -> finalize pass 和 7 -> finalize reject 两种。
        ThrowUtils.throwIf(id == null || reviewStatus == null
                , ErrorCode.PARAMS_ERROR, "审核参数错误！");
        // 2. 数据库校验
        Picture picture = this.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 3. 管理员跳过状态机图片状态流转，直接修改图片状态。（比如：把已通过的改为未通过）
        if (picture.getReviewStatus() != PictureReviewStatusEnum.AI_SUSPICIOUS.getValue()
                && picture.getReviewStatus() != PictureReviewStatusEnum.APPEAL_PENDING.getValue()) {
            // 图片持久化状态状态不为 ai suspicious / appeal pending
            Picture newPicture = new Picture();
            BeanUtils.copyProperties(pictureReviewRequest, newPicture);
            newPicture.setReviewerId(loginUser.getId());
            newPicture.setReviewStatus(reviewStatus);
            newPicture.setEditTime(new Date());
            boolean result = this.updateById(newPicture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "审核失败");
        } else {
            // 3. 利用状态机自动处理图片审核状态流转。
            Integer oldStatus = picture.getReviewStatus();
            if (oldStatus == PictureReviewStatusEnum.AI_SUSPICIOUS.getValue()) {
                StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = StateMachineUtils.getStateMachine(stateMachineFactory, ImageReviewState.AI_SUSPICIOUS);
                stateMachine.start();
                StateMachineUtils.setStateMachineExtendedState(stateMachine, ContextKey.PICTURE_OBJ_KEY, picture);
                StateMachineUtils.setStateMachineExtendedState(stateMachine, ContextKey.REVIEWER_ID_KEY, loginUser.getId());
                switch (reviewStatus) {
                    case 6 -> {
                        // 3.1 ai suspicious -> manual pass
                        stateMachine.sendEvent(ImageReviewEvent.MANUAL_REVIEW_PASS);
                    }
                    case 7 -> {
                        // 3.2 ai suspicious -> manual reject
                        stateMachine.sendEvent(ImageReviewEvent.MANUAL_REVIEW_REJECT);
                    }
                }
                stateMachine.stop();
            } else if (oldStatus == PictureReviewStatusEnum.APPEAL_PENDING.getValue()) {
                StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = StateMachineUtils.getStateMachine(stateMachineFactory, ImageReviewState.APPEAL_PENDING);
                stateMachine.start();
                StateMachineUtils.setStateMachineExtendedState(stateMachine, ContextKey.PICTURE_OBJ_KEY, picture);
                StateMachineUtils.setStateMachineExtendedState(stateMachine, ContextKey.REVIEWER_ID_KEY, loginUser.getId());
                switch (reviewStatus) {
                    case 6 -> {
                        // 3.4 appeal pending -> appeal pass
                        stateMachine.sendEvent(ImageReviewEvent.APPEAL_PASS);
                    }
                    case 7 -> {
                        // 3.5 appeal pending -> appeal reject
                        stateMachine.sendEvent(ImageReviewEvent.APPEAL_REJECT);
                    }
                }
                stateMachine.stop();
            }
        }
    }

    /**
     * 根据不同用户设置不同的审核状态，管理员上传的图片默认过审。
     */
    @Override
    public void fillPicture(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.FINAL_APPROVED.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            if (picture.getId() == null) {
                // 首次上传图片，设置为待审核
                picture.setReviewStatus(PictureReviewStatusEnum.PENDING_REVIEW.getValue());
                picture.setReviewTime(new Date());
            } else {
                // fixme 更新或者编辑图片，对图片的描述文字进行更新，暂时不做处理，以后优化。
//                picture.setReviewStatus(PictureReviewStatusEnum.AI_SUSPICIOUS.getValue());
//                picture.setReviewMessage("对图片进行了编辑或更新，进入人工审核通道");
//                picture.setReviewTime(new Date());
            }
        }
    }

    @Override
    @Deprecated
    public int uploadPictureByBatchFromBaidu(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 1. 参数校验
        Integer paraCount = pictureUploadByBatchRequest.getCount(); // 最大拉取数量
        String searchText = pictureUploadByBatchRequest.getSearchText(); // 图片关键词
        ThrowUtils.throwIf(paraCount == null || StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR, "参数错误");
        int successUploadCount = 0;
        int first = 1;
//        first = applicationContext.getBean(PictureServiceImpl.class).getScrollingPage(searchText); //拿到动态代理对象后才能激活 @Transactional
        // 3. 利用 first 和 count 构造 url，从 bing 搜索引擎中获取图片 url 集合
        String fetchURL = String.format("https://cn.bing.com/images/async?q=%s&first=%s&count=%s&mmasync=1", searchText, first, "35");
        Document document;
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
                    return successUploadCount;
                }
                Thread.sleep((int) (Math.random() * 1000));
            }
        } catch (IOException e) {
            log.info("网络请求失败: {}", fetchURL, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "网络请求超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("线程被中断", e);
        }
        return successUploadCount;
    }

    @Override
    public int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        if (ObjectUtil.isNull(count)) {
            count = 20;
        }
        if (count > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "批量上传最大图片数量为 100");
        }
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        int successUploadCount = 0;
        int first = applicationContext.getBean(PictureServiceImpl.class).getScrollingPage(searchText, count);
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
//        String uuid = UUID.randomUUID().toString().replace("-", "");
        pictureUploadRequest.setPicName(namePrefix);
        List<String> urlList = searchPicturesAPI.searchPicturesUrls(searchText, first, count, "zh");
        log.info("开始遍历上传 urlList = {}", urlList);
        for (String url : urlList) {
            try {
                this.uploadPicture(url, pictureUploadRequest, loginUser);
                Thread.sleep((int) (Math.random() * 500));
                successUploadCount++;
            } catch (BusinessException e) {
                log.info("{} 上传失败, 原因 = {}", url, e.getMessage());
                log.info("{} 上传失败", url);
            } catch (InterruptedException e) {
                log.info("线程被中断", e);
            }
        }
        log.info("上传成功数量 = {}", successUploadCount);
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
    @CacheEvict(cacheManager = "multiLevelCacheManger", value = "pictureHotKey", key = "'picture:pictureVO:' + #deleteRequest.id")
    public void deletePicture(DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        Picture oldPicture = getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        Long spaceId = oldPicture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可删除
            StpUtil.checkPermissionOr(PermissionConstants.PUBLIC_DELETE_IMAGE);
        } else {
            Space dbSpace = spaceService.getById(spaceId);

            if (dbSpace.getSpaceType() == 0) {
                // 私有空间，仅私有空间拥有者可删除
                StpUtil.checkPermission(PermissionConstants.PRIVATE_DELETE_IMAGE);
            } else {
                // 团队空间，要求包含删除权限
                StpUtil.checkPermission(PermissionConstants.TEAM_DELETE_IMAGE);
            }
        }
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(id);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
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
    }

    @Override
    @CacheEvict(cacheManager = "multiLevelCacheManger", value = "pictureHotKey", key = "'picture:pictureVO:' + #pictureEditRequest.id")
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
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 权限校验
        checkEditPermission(oldPicture);
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
                            condition = "#a0.current >= 1 && #a0.current <= 20 && #a0.spaceId == null && #a2 == false"
                    ),
                    @Cacheable(cacheManager = "redisCacheManager",
                            value = "pictureColdVOList",
                            key = "'picture:pictureVOList:' + "
                                    + "T(org.springframework.util.DigestUtils).md5DigestAsHex("
                                    + "T(cn.hutool.json.JSONUtil).toJsonStr(#a0).getBytes())",
                            condition = "#a0.current >= 21 && #a0.current <= 100 && #a0.spaceId == null && #a2 == false"
                    )
            }
    )
    public Page<PictureVO> listPictureVoByPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request, boolean checkMy) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        Long spaceId = pictureQueryRequest.getSpaceId();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 公共图库
        Integer queryPicStatus = pictureQueryRequest.getReviewStatus();
        if (spaceId == null) {
            // 普通用户只能查看已经通过审核的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.FINAL_APPROVED.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            Space dbSpace = spaceService.getById(spaceId);
            if (dbSpace == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间不存在");
            if (dbSpace.getSpaceType() == 0) {
                // 私有空间，仅私有空间拥有者可操作
                StpUtil.checkPermission(PermissionConstants.PRIVATE_VIEW_IMAGE);
            } else {
                StpUtil.checkPermission(PermissionConstants.TEAM_VIEW_IMAGE);
            }
        }

        // 查询用户个人主页
        if (checkMy) {
            User loginUser = userService.getLoginUser(request);
            Long userId = pictureQueryRequest.getUserId();
            ThrowUtils.throwIf(!userId.equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "非法请求他人上传的图片！");
            // 所有上传记录
            pictureQueryRequest.setReviewStatus(null);
            pictureQueryRequest.setNullSpaceId(true);
            if (queryPicStatus != null) {
                // 未过审上传记录
                if (queryPicStatus.equals(PictureReviewStatusEnum.FINAL_REJECTED.getValue()))
                    pictureQueryRequest.setReviewStatus(queryPicStatus);
                // 申诉中上传记录
                if (queryPicStatus.equals(PictureReviewStatusEnum.APPEAL_PENDING.getValue()))
                    pictureQueryRequest.setReviewStatus(queryPicStatus);
            }
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

    @Async("cosCleanupExecutor")
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
        if (isVideoFormat(picture.getPicFormat())) {
            String videoKey = cosKeyHandler(picture.getUrl(), cosClientHost);
            cosManager.deleteObject(videoKey);
            if (StrUtil.isNotBlank(picture.getThumbnailUrl()) && !StrUtil.equals(picture.getThumbnailUrl(), picture.getUrl())) {
                String thumbnailKey = cosKeyHandler(picture.getThumbnailUrl(), cosClientHost);
                cosManager.deleteObject(thumbnailKey);
            }
            return;
        }
        String webpKey = cosKeyHandler(picture.getUrl(), cosClientHost);
        String thumbnailKey = cosKeyHandler(picture.getThumbnailUrl(), cosClientHost);
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
        Integer spaceType = space.getSpaceType();
        if (spaceType == 0) {
            StpUtil.checkPermission(PermissionConstants.PRIVATE_VIEW_IMAGE);
        }
        if (spaceType == 1) {
            StpUtil.checkPermission(PermissionConstants.TEAM_VIEW_IMAGE);
        }
        // 3. 计算图片相似度并排序
        List<Picture> spacePicList = this.lambdaQuery().eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(spacePicList)) {
            return Collections.emptyList();
        }
        // 没有主色调的图片放到最后
        return spacePicList.stream()
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
        Space dbSpace = spaceService.getById(spaceId);
        if (dbSpace.getSpaceType() == 0) {
            // 私有空间，仅私有空间拥有者可操作
            StpUtil.checkPermission(PermissionConstants.PRIVATE_MODIFY_IMAGE);
        } else {
            StpUtil.checkPermission(PermissionConstants.TEAM_MODIFY_IMAGE);
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
        checkEditPermission(picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }

    private void checkEditPermission(Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共空间，要求有编辑权限
            StpUtil.checkPermissionOr(PermissionConstants.PUBLIC_MODIFY_IMAGE);
        } else {
            Space dbSpace = spaceService.getById(spaceId);
            if (dbSpace.getSpaceType() == 0) {
                // 私有空间，仅私有空间拥有者可操作
                StpUtil.checkPermission(PermissionConstants.PRIVATE_MODIFY_IMAGE);
            } else {
                // 团队空间，要求有编辑权限
                StpUtil.checkPermission(PermissionConstants.TEAM_MODIFY_IMAGE);
            }
        }
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
            log.info("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    /**
     * 查询批量拉取历史表，返回指定关键词滑动分页的起始页码。
     *
     * @param keyword 批量拉取图片的关键词
     * @return 返回 keyword 对应的滑动分页起始页码
     */
    @Transactional
    public int getScrollingPage(String keyword, int paraCount) {
        log.info("enter getScrollingPage,keyword {}, paraCount {}", keyword, paraCount);
        int first = 1;
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            QueryWrapper<Imagesearchhistory> historyQueryWrapper = new QueryWrapper<>();
            historyQueryWrapper.eq("searchKeyword", keyword);
            Imagesearchhistory selectedOne = historyMapper.selectOne(historyQueryWrapper);
            if (selectedOne != null) {
                first = selectedOne.getFirst() + 1;
            }
            selectedOne = ObjUtil.isNull(selectedOne) ? new Imagesearchhistory() : selectedOne;
            selectedOne.setCount(paraCount);
            selectedOne.setFirst(first);
            selectedOne.setUpdatedAt(new Date());
            selectedOne.setSearchKeyword(keyword);
            boolean success = historyService.saveOrUpdate(selectedOne); // 乐观锁
            if (!success) {
                retryCount++;
            } else {
                log.info("leave getScrollingPage,keyword {}, first {}", keyword, first);
                return first;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取页码失败");
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
        // http://resourses.collabimage.afishingcat.xin/space/1945054164708327426/2025-07-15_n3qyflw7ucq0ssg0.webp

        // 强制替换 URL 和缩略图 URL 的协议为 https
        String url = uploadPictureResult.getUrl();
        url = switchUrlFromHttpToHttps(url);
        picture.setUrl(url);
        picture.setThumbnailUrl(switchUrlFromHttpToHttps(uploadPictureResult.getThumbnailUrl()));
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
    public PictureVO getPictureVOById(long id) {
        log.info("缓存失效！");
        if (!pictureBloomFilter.contains(String.valueOf(id))) {
            return null;
        }
        Picture picture = this.getById(id);
        if (picture == null) {
            return null;
        }
        Long spaceId = picture.getSpaceId();
        Space dbSpace = spaceService.getById(spaceId);
        PictureVO pictureVO = getPictureVO(picture);
        if (dbSpace != null) {
            if (dbSpace.getSpaceType() == 0) {
                // 私有空间，仅私有空间拥有者可操作
                StpUtil.checkPermission(PermissionConstants.PRIVATE_VIEW_IMAGE);
            } else {
                StpUtil.checkPermission(PermissionConstants.TEAM_VIEW_IMAGE);
            }
            pictureVO.setSpaceType(dbSpace.getSpaceType());
        }
        return pictureVO;
    }

    @Override
    public String uploadUserAvatarPicture(MultipartFile multipartFile, User loginUser) {
        User user = userService.getById(loginUser.getId());
        if (ObjUtil.isNull(user)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "登录用户不存在！");
        // COS 中用户头像存储位置。
        String uploadPrefix = String.format("userAvatar/%s", user.getId());
        UploadPictureResult uploadPictureResult = filePictureUploader.uploadPicture(multipartFile, uploadPrefix);
        String oldAvatarUrl = user.getUserAvatar();
        // 从参数中拿到 user 头像 url 并写入 user 表的 avatar 字段。
        String avatarUrl = uploadPictureResult.getUrl();
        user.setUserAvatar(avatarUrl);
        userService.updateById(user);
        // 删除 COS 中头像
        try {
            String webpKey = cosKeyHandler(oldAvatarUrl, cosClientHost); // /public/1894627889584680961/2025-03-30_Sgs8ncAK9jWzgFpc.webp
            String thumbnailKey = webpKey.replace(".webp", "_thumbnail.png");
            String originKey = cosOriginKeyHandler(webpKey, thumbnailKey);
            cosManager.deleteObject(webpKey);
            cosManager.deleteObject(originKey);
            cosManager.deleteObject(thumbnailKey);
        } catch (Exception e) {
        }
        return avatarUrl;
    }

    @Override
    public void markPictureWithStatus(Long picId, int status, Long reviewerId, String reviewMessage) {
        lambdaUpdate().eq(Picture::getId, picId)
                .set(Picture::getReviewStatus, status)
                .set(Picture::getReviewMessage, reviewMessage)
                .set(reviewerId != null, Picture::getReviewerId, reviewerId)
                .update();
    }

    @Override
    public synchronized void warnUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户不存在！");
        if (user.getIsBlacklisted() == 1) return;
        Integer oldQuota = user.getWarningQuota();
        if (--oldQuota <= 0) user.setIsBlacklisted(1);
        user.setWarningQuota(oldQuota);
        userService.updateById(user);
    }

    @Override
    public boolean appealPendingPicture(Picture picture, User loginUser) {
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = StateMachineUtils.getStateMachine(stateMachineFactory, ImageReviewState.FINAL_REJECTED);
        StateMachineUtils.setStateMachineExtendedState(stateMachine, ContextKey.PICTURE_OBJ_KEY, picture);
        stateMachine.start();
        boolean result = true;
        stateMachine.sendEvent(ImageReviewEvent.APPEAL_SUBMIT);
        if (stateMachine.getState().getId().equals(ImageReviewState.FINAL_REJECTED)) result = false; // 状态切换失败
        return result;
    }

    private void sentReviewContentToMessageQueue(Picture picture) {
        ReviewContentMessage reviewContentMessage = new ReviewContentMessage();
        reviewContentMessage.setPicId(picture.getId());
        amqpTemplate.convertAndSend(MQConstants.AUDIT_CONTENT_EXCHANGE, MQConstants.ROUTING_AUDIT_CONTENT, reviewContentMessage);
    }

    private String normalizeMediaType(String mediaType) {
        if (StrUtil.isBlank(mediaType)) {
            return MEDIA_TYPE_IMAGE;
        }
        String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(!MEDIA_TYPE_IMAGE.equals(normalized) && !MEDIA_TYPE_VIDEO.equals(normalized),
                ErrorCode.PARAMS_ERROR, "mediaType 仅支持 image 或 video");
        return normalized;
    }

    private boolean isVideoFormat(String format) {
        if (StrUtil.isBlank(format)) {
            return false;
        }
        return ALLOW_VIDEO_FORMAT_LIST.contains(format.trim().toLowerCase(Locale.ROOT));
    }

    private UploadPictureResult uploadVideo(MultipartFile multipartFile, String uploadPathPrefix) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空！");
        ThrowUtils.throwIf(multipartFile.getSize() <= 0, ErrorCode.PARAMS_ERROR, "文件为空！");
        ThrowUtils.throwIf(multipartFile.getSize() > SINGLE_VIDEO_MAX_SIZE, ErrorCode.PARAMS_ERROR, "视频大小不能超过 200MB！");
        String originalFilename = multipartFile.getOriginalFilename();
        ThrowUtils.throwIf(StrUtil.isBlank(originalFilename), ErrorCode.PARAMS_ERROR, "文件名不能为空！");
        String suffix = FileUtil.getSuffix(originalFilename).toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(!ALLOW_VIDEO_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "视频类型错误！");

        String uuid = RandomUtil.randomString(16);
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, suffix);
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        File tempFile = null;
        try {
            tempFile = File.createTempFile("video_upload_", "." + suffix);
            multipartFile.transferTo(tempFile);
            cosManager.putObject(uploadPath, tempFile);
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(StrUtil.removeSuffix(cosClientHost, "/") + uploadPath);
            uploadPictureResult.setThumbnailUrl(uploadPictureResult.getUrl());
            uploadPictureResult.setPicName(originalFilename);
            uploadPictureResult.setPicSize(tempFile.length());
            uploadPictureResult.setPicFormat(suffix);
            uploadPictureResult.setPicWidth(0);
            uploadPictureResult.setPicHeight(0);
            uploadPictureResult.setPicScale(0D);
            uploadPictureResult.setPicColor(null);
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("video upload error, filepath: {}", uploadPath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "视频上传失败");
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("delete temp video file failed, path={}", tempFile.getAbsolutePath());
            }
        }
    }

    private void fillVideoReview(Picture picture, User loginUser) {
        picture.setReviewStatus(PictureReviewStatusEnum.FINAL_APPROVED.getValue());
        picture.setReviewerId(loginUser.getId());
        picture.setReviewMessage("视频资源默认过审");
        picture.setReviewTime(new Date());
    }
}




