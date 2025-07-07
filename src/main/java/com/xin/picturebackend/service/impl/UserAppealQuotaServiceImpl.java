package com.xin.picturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.mapper.UserAppealQuotaMapper;
import com.xin.picturebackend.model.entity.UserAppealQuota;
import com.xin.picturebackend.service.UserAppealQuotaService;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;

/**
 * 用户申诉配额服务实现类
 */
@Service
public class UserAppealQuotaServiceImpl extends ServiceImpl<UserAppealQuotaMapper, UserAppealQuota>
        implements UserAppealQuotaService {

    private static final int MAX_WEEKLY_APPEAL = 2;

    @Override
    public void increaseAppeal(Long userId) {
        Date weekStartDate = getCurrentWeekStartDate();
        UserAppealQuota quota = getOrInitQuota(userId, weekStartDate);
        int trueQuota = Math.min(quota.getAppealUsed() + 1, MAX_WEEKLY_APPEAL);
        quota.setAppealUsed(trueQuota);
        quota.setUpdatedTime(new Date());
        this.updateById(quota);
    }

    @Override
    public synchronized void decreaseAppeal(Long userId) {
        Date weekStartDate = getCurrentWeekStartDate();
        UserAppealQuota quota = getOrInitQuota(userId, weekStartDate);

        if (quota.getAppealUsed() > 0) {
            quota.setAppealUsed(quota.getAppealUsed() - 1);
            quota.setUpdatedTime(new Date());
            this.updateById(quota);
        }
    }

    @Override
    public boolean canAppeal(Long userId) {
        UserAppealQuota one = lambdaQuery().eq(UserAppealQuota::getUserId, userId)
                .eq(UserAppealQuota::getWeekStartDate, getCurrentWeekStartDate())
                .one();
        return one.getAppealUsed() > 0;
    }

    private UserAppealQuota getOrInitQuota(Long userId, Date weekStartDate) {
        UserAppealQuota quota = this.lambdaQuery()
                .eq(UserAppealQuota::getUserId, userId)
                .eq(UserAppealQuota::getWeekStartDate, weekStartDate)
                .one();

        if (quota == null) {
            quota = new UserAppealQuota();
            quota.setUserId(userId);
            quota.setWeekStartDate(weekStartDate);
            quota.setAppealUsed(MAX_WEEKLY_APPEAL);
            Date now = new Date();
            quota.setCreatedTime(now);
            quota.setUpdatedTime(now);
            this.save(quota);
        }

        return quota;
    }

    /**
     * 获取本周周一的日期（清零时间）
     */
    private Date getCurrentWeekStartDate() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        // 清除时间部分
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}




