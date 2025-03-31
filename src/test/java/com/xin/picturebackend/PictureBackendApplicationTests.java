package com.xin.picturebackend;

import cn.dev33.satoken.stp.StpUtil;
import com.xin.picturebackend.apiintegration.com.pixabay.api.SearchPicturesAPI;
import com.xin.picturebackend.model.dto.picture.PictureEditByBatchRequest;
import com.xin.picturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.xin.picturebackend.model.dto.picture.PictureUploadRequest;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;


@SpringBootTest
class PictureBackendApplicationTests {
    @Autowired
    private SpaceService spaceService;

    @Resource
    private SearchPicturesAPI searchPicturesAPI;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;
    void uploadPicture(Object resourceSource, PictureUploadRequest pictureUploadRequest, User user) {
    }
    @Test
    void testSearchPicturesAPI() {
        List<String> urls = searchPicturesAPI.searchPicturesUrls("cat", 1, 10, "en");
        System.out.println(urls);
    }

    @Test
    void testUploadByBatch() {
        // 构造一个 User 对象
        User user = userService.getById(1894627889584680961L);
        StpUtil.login(user.getId());
        // 构造一个 PictureUploadByBatchRequest 对象
        PictureUploadByBatchRequest pictureUploadByBatchRequest = new PictureUploadByBatchRequest();
        pictureUploadByBatchRequest.setSearchText("老虎");
        pictureUploadByBatchRequest.setCount(10);
        int i = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, user);
        System.out.println(i);
    }
}
