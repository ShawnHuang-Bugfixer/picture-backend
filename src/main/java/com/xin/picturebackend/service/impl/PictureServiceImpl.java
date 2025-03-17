package com.xin.picturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.manager.upload.FilePictureUpload;
import com.xin.picturebackend.manager.upload.URLPictureUpload;
import com.xin.picturebackend.mapper.ImagesearchhistoryMapper;
import com.xin.picturebackend.model.dto.file.UploadPictureResult;
import com.xin.picturebackend.model.dto.picture.PictureQueryRequest;
import com.xin.picturebackend.model.dto.picture.PictureReviewRequest;
import com.xin.picturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.xin.picturebackend.model.dto.picture.PictureUploadRequest;
import com.xin.picturebackend.model.entity.Imagesearchhistory;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.PictureReviewStatusEnum;
import com.xin.picturebackend.model.vo.PictureVO;
import com.xin.picturebackend.model.vo.UserVO;
import com.xin.picturebackend.service.ImagesearchhistoryService;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.mapper.PictureMapper;
import com.xin.picturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
//import org.redisson.api.RBloomFilter;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.support.NullValue;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 根据 resourceSource 类型，上传图片到公共图库。此过程中自动设置审核状态，并且填充其他信息最后保存到数据库。
     * 未携带图片 id 时，直接上传图片到 COS 中，并且填充其他信息后保存到数据库。携带 id 时，重新上传到 COS 中，修改数据库中指定 id 的 picture 的对象存储地址 url。
     * fixme : 携带 id 时，只修改了数据库中指定 id 的 picture 的对象存储地址 url，并未删除原有的图片。
     *
     * @param pictureUploadRequest 携带图片 id, 文件 url, 文件名
     * @param user                 用户信息
     * @return 返回 PictureVO 视图对象，包含图片 id
     */
    @Override
    public PictureVO uploadPicture(Object resourceSource, PictureUploadRequest pictureUploadRequest, User user) {
        // 1. 参数校验
        ThrowUtils.throwIf(user == null, ErrorCode.NO_AUTH_ERROR);
        Long pictureId = null;
        String picName = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
            picName = pictureUploadRequest.getPicName();
        }
        // 2. 判断是否为重新上传，即判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(user.getId()) && !userService.isAdmin(user)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
        // 3. 上传图片到公共图库
        String uploadPrefix = String.format("public/%s", user.getId());
        UploadPictureResult uploadPictureResult = null;
        // 判断使用文件上传还是url上传
        if (resourceSource instanceof MultipartFile) {
            MultipartFile file = (MultipartFile) resourceSource;
            uploadPictureResult = filePictureUploader.uploadPicture(file, uploadPrefix);
        } else {
            String resourceUrl = (String) resourceSource;
            uploadPictureResult = urlPictureUploader.uploadPicture(resourceUrl, uploadPrefix);
        }
        Picture picture = getPicture(picName, user, uploadPictureResult, pictureId);
        // 重置审核状态
        fillPicture(picture, user);
        // 4. 保存到数据库
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        return PictureVO.objToVo(picture);
    }

    /**
     * 根据传入请求，动态拼接 sql 并返回 QueryWrapper
     *
     * @param pictureQueryRequest 图片查询请求，封装查询条件
     * @return 返回构造好的 QueryWrapper<Picture> 对象
     */
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

    /**
     * 从 picturePage 中获取 userid 集合，由该集合获取 user 对象集合，封装 pictureVOPage，最后将
     * user 对象转换为 userVo 封装到 pictureVOPage
     *
     * @param picturePage 原始数据分页
     * @return 返回封装后的 pictureVO 视图分页
     */
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

    /**
     * 校验图片格式
     *
     * @param picture 图片对象
     */
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

    /**
     * 修改图片审核状态为 ‘通过审核’或者‘未通过审核’
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            当前登录用户
     */
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

    /**
     * 填充图片审核状态：默认为待审核，管理员上传的图片默认为通过审核
     *
     * @param picture   Not Null 图片对象
     * @param loginUser Not Null 登录用户信息
     */
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

    /**
     * 批量从 bing 拉取指定关键词 keyword 的图片，返回成功上传 COS 的数量。
     *
     * @param pictureUploadByBatchRequest 包含 关键词和最大抓取数量
     * @param loginUser                   登录用户
     * @return 返回 上传数量
     */
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
                Thread.sleep(500);
            }
        } catch (Exception e) {
            log.error("网络错误，请稍后重试", e);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "网络错误，请稍后重试");
        }
        return successUploadCount;
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
            boolean success = historyService.saveOrUpdate(selectedOne);
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
     *
     * @param id picture id
     * @return 返回去敏后数据 pictureVO
     */
    @Override
    @Cacheable(cacheManager = "multiLevelCacheManger", value = "pictureHotKey", key = "'picture:pictureVO:' + #id", sync = true)
    public PictureVO getPictureVOById(long id) {
        log.error("缓存失效！");
        if (!pictureBloomFilter.contains(String.valueOf(id))) {
            return null;
        }
        Picture picture = this.getById(id);
        if (picture == null) {
            return null;
        }
        return getPictureVO(picture);
    }
}




