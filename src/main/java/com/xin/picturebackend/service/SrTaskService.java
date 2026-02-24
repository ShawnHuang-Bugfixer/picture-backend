package com.xin.picturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xin.picturebackend.model.dto.sr.SrTaskCreateRequest;
import com.xin.picturebackend.model.dto.sr.SrTaskQueryRequest;
import com.xin.picturebackend.model.entity.SrTask;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.model.vo.sr.SrTaskVO;

public interface SrTaskService extends IService<SrTask> {

    Long createTask(SrTaskCreateRequest request, User loginUser);

    SrTaskVO getSrTaskVOById(Long id, User loginUser);

    Page<SrTaskVO> listMyTaskByPage(SrTaskQueryRequest request, User loginUser);

    void handleResultMessage(SrResultMessage resultMessage);
}

