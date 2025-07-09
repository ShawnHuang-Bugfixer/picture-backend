create database if not exists picture;

use picture;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                       null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId)              -- 提升基于用户 ID 的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;

-- 修改图片表，增加审核状态字段
ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus  INT DEFAULT 0 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    ADD COLUMN reviewContentMessage VARCHAR(512)  NULL COMMENT '审核信息',
    ADD COLUMN reviewerId    BIGINT        NULL COMMENT '审核人 ID',
    ADD COLUMN reviewTime    DATETIME      NULL COMMENT '审核时间';

-- 修改图片表，增加感知哈希值字段和索引
ALTER TABLE picture
    ADD COLUMN pHash VARCHAR(64) DEFAULT NULL COMMENT '感知哈希值',
    ADD INDEX idx_pHash (pHash);

-- 修改图片表，新增缩略图url
ALTER TABLE picture
    -- 添加新列
    ADD COLUMN thumbnailUrl varchar(512) NULL COMMENT '缩略图 url';

-- 修改图片表，新增空间列
ALTER TABLE picture
    ADD COLUMN spaceId bigint null comment '空间 id（为空表示公共空间）';

-- 修改图片表，新增主色调
ALTER TABLE picture
    ADD COLUMN picColor varchar(16) null comment '图片主色调';


-- 创建索引
CREATE INDEX idx_spaceId ON picture (spaceId);


-- 创建基于 reviewStatus 列的索引（审核状态）
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);

-- 创建管理员批量拉取图片信息历史记录
CREATE TABLE IF NOT EXISTS imageSearchHistory
(
    id            bigint auto_increment comment 'id' primary key,
    searchKeyword VARCHAR(255) NOT NULL COMMENT '搜索关键词',
    first         INT          NOT NULL COMMENT '分页参数 first',
    count         INT          NOT NULL COMMENT '分页参数 count',
    version       INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号（初始值为1，每次更新+1）',
    createdAt     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updatedAt     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY idx_unique_keyword (searchKeyword)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 空间表
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 null comment '空间图片的最大数量',
    totalSize  bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    -- 索引设计
    index idx_userId (userId),        -- 提升基于用户的查询效率
    index idx_spaceName (spaceName),  -- 提升基于空间名称的查询效率
    index idx_spaceLevel (spaceLevel) -- 提升按空间级别查询的效率
) comment '空间' collate = utf8mb4_unicode_ci;

-- 修改空间表，增加空间类型字段
ALTER TABLE space
    ADD COLUMN spaceType int default 0 not null comment '空间类型：0-私有 1-团队';

CREATE INDEX idx_spaceType ON space (spaceType);

-- 空间成员表
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    -- 索引设计
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 唯一索引，用户在一个空间中只能有一个角色
    INDEX idx_spaceId (spaceId),                    -- 提升按空间查询的性能
    INDEX idx_userId (userId)                       -- 提升按用户查询的性能
) comment '空间用户关联' collate = utf8mb4_unicode_ci;

-- 审核消息表
CREATE TABLE review_message
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT NOT NULL COMMENT '接收用户 ID',
    content    TEXT   NOT NULL COMMENT '消息内容',
    status     TINYINT  DEFAULT 0 COMMENT '消息状态：0=未读，1=已读',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    read_at    DATETIME DEFAULT NULL COMMENT '阅读时间',

    INDEX idx_user_id (user_id)
) COMMENT ='审核消息表';

# 为审核机制增加配额表，用户一周内申诉次数。
CREATE TABLE `user_appeal_quota`
(
    `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT   NOT NULL COMMENT '用户ID',
    `week_start_date` DATE     NOT NULL COMMENT '当前记录的起始周日期(如周一)',
    `appeal_used`     INT      NOT NULL DEFAULT 0 COMMENT '已使用的申诉次数(最大为2)',
    `updated_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_week` (`user_id`, `week_start_date`),
    CONSTRAINT `chk_appeal_used` CHECK (`appeal_used` BETWEEN 0 AND 2)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户申诉配额表';

# 为审核机制增加黑名单和警告次数字段。
ALTER TABLE user
    ADD warning_quota INT DEFAULT 3 COMMENT '剩余警告次数（默认为3，减到0即拉黑）',
    ADD is_blacklisted BOOLEAN DEFAULT FALSE COMMENT '是否已被拉黑';

ALTER TABLE picture
    MODIFY COLUMN reviewStatus INT DEFAULT 0 COMMENT '审核状态（参见 PictureReviewStatusEnum）';



