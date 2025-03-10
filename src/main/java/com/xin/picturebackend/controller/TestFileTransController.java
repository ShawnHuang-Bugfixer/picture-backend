package com.xin.picturebackend.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.xin.picturebackend.annotation.AuthCheck;
import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.constant.UserConstant;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.manager.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * TODO 验证 SDK 操作对象存储
 *
 * @author 黄兴鑫
 * @since 2025/2/27 15:30
 */
@RestController
@RequestMapping("/test")
@Slf4j
public class TestFileTransController {
    @Resource
    private CosManager cosManager;

    /**
     * Handles the upload of a file to the object storage.
     *
     * <p>This endpoint requires the user to have admin role permissions. It takes a multipart file
     * as input, saves it temporarily, uploads it to the object storage using Tencent COS, and
     * returns the file path as a response.</p>
     *
     * @param multipartFile the file to be uploaded, provided as a multipart request part
     * @return a {@link BaseResponse} containing the file path if the upload is successful
     * @throws BusinessException if any error occurs during file upload or temporary file handling
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> testUpload(@RequestPart("file") MultipartFile multipartFile) {
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("%s/%s", "test", filename);
        File file = null;
        try {
            file = File.createTempFile(filepath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            return ResultUtils.success(filepath);
        } catch (IOException e) {
            log.error("file upload error, filepath: {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                if (!file.delete()) {
                    log.error("file delete error, filepath: {}", filepath);
                }
            }

        }
    }

    @GetMapping("/download")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public void testDownloadFile(String filepath, HttpServletResponse response) {
        COSObject cosObject = cosManager.getObject(filepath);
        COSObjectInputStream cosObjectInputStream = null;
        try {
            cosObjectInputStream = cosObject.getObjectContent();
            byte[] byteArray = IOUtils.toByteArray(cosObjectInputStream);
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + filepath);
            response.getOutputStream().write(byteArray);
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("file download error, filepath: {}", filepath, e);
            throw new RuntimeException(e);
        } finally {
            if (cosObjectInputStream != null) {
                try {
                    cosObjectInputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


}
