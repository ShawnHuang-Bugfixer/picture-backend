package com.xin.picturebackend;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.xin.picturebackend.auth.constant.PermissionConstants;
import com.xin.picturebackend.auth.enums.PermissionEnum;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.file.UploadPictureResult;
import com.xin.picturebackend.model.dto.picture.PictureUploadRequest;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SpaceTypeEnum;
import com.xin.picturebackend.model.vo.PictureVO;
import com.xin.picturebackend.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;


@SpringBootTest
class PictureBackendApplicationTests {
    @Autowired
    private SpaceService spaceService;

    public PictureVO uploadPicture(Object resourceSource, PictureUploadRequest pictureUploadRequest, User user) {
       return null;
    }
}
