package com.xin.picturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xin.picturebackend.model.dto.file.UploadPictureResult;
import com.xin.picturebackend.model.dto.picture.*;
import com.xin.picturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.vo.PictureVO;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Lenovo
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-02-27 16:22:08
 */
public interface PictureService extends IService<Picture> {

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

    int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    PictureVO getPictureVOById(long id);

    void updatePicture(PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request);

    Page<PictureVO> listPictureVoByPage(PictureQueryRequest pictureQueryRequest);

    @Async
    void clearPictureFile(Picture oldPicture);
}
