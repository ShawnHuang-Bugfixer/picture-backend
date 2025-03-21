package com.xin.picturebackend.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.space.*;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SpaceLevelEnum;
import com.xin.picturebackend.model.vo.SpaceVO;
import com.xin.picturebackend.model.vo.UserVO;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.mapper.SpaceMapper;
import com.xin.picturebackend.service.UserService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Lenovo
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-03-20 11:15:50
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {
    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        // 参数校验
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) return queryWrapper;
        // 获取查询条件
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.like(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public SpaceVO getSpaceVO(Space space) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage) {
        List<Space> records = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (records == null || records.isEmpty()) return spaceVOPage;
        Set<Long> userIds = records.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        List<SpaceVO> spaceVOList = records.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userMap.containsKey(userId)) {
                user = userMap.get(userId);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

    @Override
    public SpaceVO getSpaceVOById(long id) {
        Space space = this.getById(id);
        if (space == null) {
            return null;
        }
        return getSpaceVO(space);
    }

    @Override
    public void updateSpace(SpaceUpdateRequest spaceUpdateRequest) {
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        validSpace(space, false);
        // 判断是否存在于数据库
        long id = spaceUpdateRequest.getId();
        Space oldSpace = this.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        //
        fillSpace(space);
        // 操作数据库
        boolean result = updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public Page<SpaceVO> listSpaceVoByPage(SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        Page<Space> spacePage = this.page(new Page<>(current, size),
                this.getQueryWrapper(spaceQueryRequest));
        return getSpaceVOPage(spacePage);
    }

    @Override
    public void fillSpace(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值填充
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        fillSpace(space);
        validSpace(space, true);
        Long id = loginUser.getId();
        space.setUserId(id);
        // 权限校验:管理员才能指定空间级别
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 保证用户私有空间唯一
        RLock lock = redissonClient.getLock("picture:addSpace:" + id);
        boolean locked = false;
        Long newSpaceId = null;
        try {
            // 尝试获取锁，等待1秒，锁持有时间为3秒
            locked = lock.tryLock(1, 3, TimeUnit.SECONDS);

            if (locked) {
                // fixme 研究这里为什么要加事务？？
                newSpaceId = transactionTemplate.execute(status -> {
                    // 查询数据库，检查是否已存在未删除的空间
                    Space dbSpace = this.lambdaQuery().eq(Space::getUserId, id).one();
                    if (dbSpace != null && dbSpace.getIsDelete() == 0) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR, "每个用户只允许创建一个私有空间");
                    }

                    // 保存空间信息
                    boolean save = save(space);
                    if (!save) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR);
                    }
                    return space.getId();
                });
            } else {
                // 获取锁失败，抛出异常或进行其他处理
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            // 处理中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作被中断");
        } finally {
            // 确保锁被释放
            if (locked) {
                lock.unlock();
            }
        }
        return Optional.ofNullable(newSpaceId).orElse(-1L);
    }

}




