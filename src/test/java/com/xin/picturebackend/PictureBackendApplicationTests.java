package com.xin.picturebackend;

import cn.dev33.satoken.stp.StpUtil;
import com.xin.picturebackend.apiintegration.com.pixabay.api.SearchPicturesAPI;
import com.xin.picturebackend.auth.StpInterfaceImpl;
import com.xin.picturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.xin.picturebackend.model.dto.picture.PictureUploadRequest;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.vo.PictureVO;
import com.xin.picturebackend.model.vo.SpaceVO;
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

    @Resource
    private StpInterfaceImpl stpInterface;

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


    @Test
    void testGetPermissions() {
        // 构造一个 User 对象
        User user = userService.getById(1894627889584680961L);
        StpUtil.login(user.getId());
        List<String> permissions = StpUtil.getPermissionList();
        System.out.println(permissions);
        StpUtil.logout();
        User commonUser = userService.getById(1902895992949112833L);
        StpUtil.login(commonUser.getId());
        List<String> commonUserPermissions = StpUtil.getPermissionList();
        System.out.println(commonUserPermissions);
    }


    /**
     * todo 测试正确获取不同身份对应权限
     *      1. 公共空间拥有者 两空普通用户
     *      2. 公共空间图片拥有者 图片非空普通用户
     *      3. 系统管理员 两空系统管理用户
     *      4. 私人空间拥有者 空间普通用户
     *      5. 团队空间拥有者 空间普通用户
     *      6. 团队空间编辑者 空间普通用户
     *      7. 团队空间浏览者 空间普通用户
     */
    @Test
    void testGetPermissionFromUserInterface() {
        System.out.println("--------------公共空间拥有者");
        List<String> permissions = stpInterface.getPermissions(null, 1902895992949112833L, null, null);
        System.out.println(permissions);
        System.out.println("--------------公共空间图片拥有者");
        permissions = stpInterface.getPermissions(null, 1894627889584680961L, 1906366833817010177L, null);
        System.out.println(permissions);
        System.out.println("--------------系统管理员");
        permissions = stpInterface.getPermissions(null, 1894627889584680961L, null, null);
        System.out.println(permissions);
        System.out.println("--------------私人空间拥有者");
        permissions = stpInterface.getPermissions(1906728908816793601L, 1894627889584680961L, null, null);
        System.out.println(permissions);
        System.out.println("--------------团队空间拥有者");
        permissions = stpInterface.getPermissions(1906623132106461185L, 1894627889584680961L, null, null);
        System.out.println(permissions);
        System.out.println("--------------团队空间编辑者");
        permissions = stpInterface.getPermissions(1906623132106461185L, 1902895992949112833L, null, null);
        System.out.println(permissions);
    }

    @Test
    void testGetVO() {
        PictureVO pictureVOById = pictureService.getPictureVOById(1906366833817010177L);
        SpaceVO spaceVOById = spaceService.getSpaceVOById(1906623132106461185L);
        System.out.println(pictureVOById);
        System.out.println(spaceVOById);
    }
}
