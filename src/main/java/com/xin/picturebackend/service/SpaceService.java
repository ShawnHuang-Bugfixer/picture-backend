package com.xin.picturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xin.picturebackend.apiintegration.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xin.picturebackend.model.dto.picture.CreatePictureOutPaintingTaskRequest;
import com.xin.picturebackend.model.dto.space.SpaceAddRequest;
import com.xin.picturebackend.model.dto.space.SpaceQueryRequest;
import com.xin.picturebackend.model.dto.space.SpaceUpdateRequest;
import com.xin.picturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.vo.SpaceVO;
import org.springframework.cache.annotation.Cacheable;

import javax.servlet.http.HttpServletRequest;

/**
* @author Lenovo
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-03-20 11:15:50
*/
public interface SpaceService extends IService<Space> {

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    SpaceVO getSpaceVO(Space space);

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage);

    /**
     * 校验空间对象.
     * 如果是上传 add == true,额外执行添加空间的校验逻辑.
     * 如果是修改 add == false,仅仅执行修改空间的逻辑校验
     *
     * @param space 图片对象
     */
    void validSpace(Space space, boolean add);

    SpaceVO getSpaceVOById(long id);

    void updateSpace(SpaceUpdateRequest spaceUpdateRequest);

    Page<SpaceVO> listSpaceVoByPage(SpaceQueryRequest spaceQueryRequest);

    /**
     * 填充空间 maxSize, maxCount
     * 如果 space 未指定maxSize,maxCount,则根据空间级别，自动填充限额
     *
     * @param space 空间对象
     */
    void fillSpace(Space space);

    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    @Deprecated
    void checkSpaceAuth(User loginUser, Space space);
}
