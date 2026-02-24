package com.xin.picturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xin.picturebackend.model.dto.sr.SrTaskResultQueryRequest;
import com.xin.picturebackend.model.dto.sr.SrTaskSpaceResultQueryRequest;
import com.xin.picturebackend.model.entity.SrTask;
import com.xin.picturebackend.model.entity.SrTaskResult;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.model.vo.sr.SrTaskResultVO;

public interface SrTaskResultService extends IService<SrTaskResult> {

    Page<SrTaskResultVO> listMyResultByPage(SrTaskResultQueryRequest request, User loginUser);

    Page<SrTaskResultVO> listSpaceResultByPage(SrTaskSpaceResultQueryRequest request, User loginUser);

    void saveOrUpdateSuccessResult(SrTask srTask, SrResultMessage resultMessage);
}
