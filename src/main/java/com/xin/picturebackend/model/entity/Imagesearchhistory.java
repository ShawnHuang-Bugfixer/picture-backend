package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * 
 * @TableName imagesearchhistory
 */
@TableName(value ="imagesearchhistory")
@Data
public class Imagesearchhistory {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 搜索关键词
     */
    private String searchKeyword;

    /**
     * 分页参数 first
     */
    private Integer first;

    /**
     * 分页参数 count
     */
    private Integer count;

    /**
     * 乐观锁版本号（初始值为1，每次更新+1）
     */
    @Version
    private Integer version;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}