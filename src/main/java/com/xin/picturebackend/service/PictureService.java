package com.xin.picturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xin.picturebackend.apiintegration.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xin.picturebackend.common.DeleteRequest;
import com.xin.picturebackend.model.dto.picture.*;
import com.xin.picturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Lenovo
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-02-27 16:22:08
 */
public interface PictureService extends IService<Picture> {

    /**
     * 根据 resourceSource 类型，上传图片到公共图库。此过程中自动设置审核状态，并且填充其他信息最后保存到数据库。
     *
     * @param pictureUploadRequest 携带图片 id, 文件 url, 文件名
     * @param user                 用户信息
     * @return 返回 PictureVO 视图对象，包含图片 id
     */
    PictureVO uploadPicture(Object resourceSource, PictureUploadRequest pictureUploadRequest, User user);

    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    PictureVO getPictureVO(Picture picture);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage);

    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    void fillPicture(Picture picture, User loginUser);

    /**
     * 批量从 bing 拉取指定关键词 keyword 的图片，返回成功上传 COS 的数量。
     *
     * @param pictureUploadByBatchRequest 包含 关键词和最大抓取数量
     * @param loginUser                   登录用户
     * @return 返回 上传数量
     */
    @Deprecated
    int uploadPictureByBatchFromBaidu(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);

    PictureVO getPictureVOById(long id);

    void updatePicture(PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request);

    Page<PictureVO> listPictureVoByPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request, boolean checkMy);

    void clearPictureFile(Picture oldPicture);

    @Deprecated
    default void checkPictureAuth(User loginUser, Picture picture) {};

    void deletePicture(DeleteRequest deleteRequest, HttpServletRequest request);

    void editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request);

    int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    String uploadUserAvatarPicture(MultipartFile multipartFile, User loginUser);

    void markPictureWithStatus(Long picId, int status, Long reviewerId, String reviewMessage);

    void warnUser(Long userId);

    boolean appealPendingPicture(Picture picture, User loginUser);
}
