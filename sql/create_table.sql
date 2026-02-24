create database if not exists picture;

use picture;

-- 鐢ㄦ埛琛?
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '璐﹀彿',
    userPassword varchar(512)                           not null comment '瀵嗙爜',
    userName     varchar(256)                           null comment '鐢ㄦ埛鏄电О',
    userAvatar   varchar(1024)                          null comment '鐢ㄦ埛澶村儚',
    userProfile  varchar(512)                           null comment '鐢ㄦ埛绠€浠?,
    userRole     varchar(256) default 'user'            not null comment '鐢ㄦ埛瑙掕壊锛歶ser/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '缂栬緫鏃堕棿',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '鍒涘缓鏃堕棿',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '鏇存柊鏃堕棿',
    isDelete     tinyint      default 0                 not null comment '鏄惁鍒犻櫎',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '鐢ㄦ埛' collate = utf8mb4_unicode_ci;

-- 鍥剧墖琛?
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '鍥剧墖 url',
    name         varchar(128)                       not null comment '鍥剧墖鍚嶇О',
    introduction varchar(512)                       null comment '绠€浠?,
    category     varchar(64)                        null comment '鍒嗙被',
    tags         varchar(512)                       null comment '鏍囩锛圝SON 鏁扮粍锛?,
    picSize      bigint                             null comment '鍥剧墖浣撶Н',
    picWidth     int                                null comment '鍥剧墖瀹藉害',
    picHeight    int                                null comment '鍥剧墖楂樺害',
    picScale     double                             null comment '鍥剧墖瀹介珮姣斾緥',
    picFormat    varchar(32)                        null comment '鍥剧墖鏍煎紡',
    userId       bigint                             not null comment '鍒涘缓鐢ㄦ埛 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '鍒涘缓鏃堕棿',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '缂栬緫鏃堕棿',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '鏇存柊鏃堕棿',
    isDelete     tinyint  default 0                 not null comment '鏄惁鍒犻櫎',
    INDEX idx_name (name),                 -- 鎻愬崌鍩轰簬鍥剧墖鍚嶇О鐨勬煡璇㈡€ц兘
    INDEX idx_introduction (introduction), -- 鐢ㄤ簬妯＄硦鎼滅储鍥剧墖绠€浠?
    INDEX idx_category (category),         -- 鎻愬崌鍩轰簬鍒嗙被鐨勬煡璇㈡€ц兘
    INDEX idx_tags (tags),                 -- 鎻愬崌鍩轰簬鏍囩鐨勬煡璇㈡€ц兘
    INDEX idx_userId (userId)              -- 鎻愬崌鍩轰簬鐢ㄦ埛 ID 鐨勬煡璇㈡€ц兘
) comment '鍥剧墖' collate = utf8mb4_unicode_ci;

-- 淇敼鍥剧墖琛紝澧炲姞瀹℃牳鐘舵€佸瓧娈?
ALTER TABLE picture
    -- 娣诲姞鏂板垪
    ADD COLUMN reviewStatus         INT DEFAULT 0 NOT NULL COMMENT '瀹℃牳鐘舵€侊細0-寰呭鏍? 1-閫氳繃; 2-鎷掔粷',
    ADD COLUMN reviewContentMessage VARCHAR(512)  NULL COMMENT '瀹℃牳淇℃伅',
    ADD COLUMN reviewerId           BIGINT        NULL COMMENT '瀹℃牳浜?ID',
    ADD COLUMN reviewTime           DATETIME      NULL COMMENT '瀹℃牳鏃堕棿';

-- 淇敼鍥剧墖琛紝澧炲姞鎰熺煡鍝堝笇鍊煎瓧娈靛拰绱㈠紩
ALTER TABLE picture
    ADD COLUMN pHash VARCHAR(64) DEFAULT NULL COMMENT '鎰熺煡鍝堝笇鍊?,
    ADD INDEX idx_pHash (pHash);

-- 淇敼鍥剧墖琛紝鏂板缂╃暐鍥緐rl
ALTER TABLE picture
    -- 娣诲姞鏂板垪
    ADD COLUMN thumbnailUrl varchar(512) NULL COMMENT '缂╃暐鍥?url';

-- 淇敼鍥剧墖琛紝鏂板绌洪棿鍒?
ALTER TABLE picture
    ADD COLUMN spaceId bigint null comment '绌洪棿 id锛堜负绌鸿〃绀哄叕鍏辩┖闂达級';

-- 淇敼鍥剧墖琛紝鏂板涓昏壊璋?
ALTER TABLE picture
    ADD COLUMN picColor varchar(16) null comment '鍥剧墖涓昏壊璋?;


-- 鍒涘缓绱㈠紩
CREATE INDEX idx_spaceId ON picture (spaceId);


-- 鍒涘缓鍩轰簬 reviewStatus 鍒楃殑绱㈠紩锛堝鏍哥姸鎬侊級
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);

-- 鍒涘缓绠＄悊鍛樻壒閲忔媺鍙栧浘鐗囦俊鎭巻鍙茶褰?
CREATE TABLE IF NOT EXISTS imageSearchHistory
(
    id            bigint auto_increment comment 'id' primary key,
    searchKeyword VARCHAR(255) NOT NULL COMMENT '鎼滅储鍏抽敭璇?,
    first         INT          NOT NULL COMMENT '鍒嗛〉鍙傛暟 first',
    count         INT          NOT NULL COMMENT '鍒嗛〉鍙傛暟 count',
    version       INT          NOT NULL DEFAULT 1 COMMENT '涔愯閿佺増鏈彿锛堝垵濮嬪€间负1锛屾瘡娆℃洿鏂?1锛?,
    createdAt     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updatedAt     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY idx_unique_keyword (searchKeyword)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 绌洪棿琛?
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '绌洪棿鍚嶇О',
    spaceLevel int      default 0                 null comment '绌洪棿绾у埆锛?-鏅€氱増 1-涓撲笟鐗?2-鏃楄埌鐗?,
    maxSize    bigint   default 0                 null comment '绌洪棿鍥剧墖鐨勬渶澶ф€诲ぇ灏?,
    maxCount   bigint   default 0                 null comment '绌洪棿鍥剧墖鐨勬渶澶ф暟閲?,
    totalSize  bigint   default 0                 null comment '褰撳墠绌洪棿涓嬪浘鐗囩殑鎬诲ぇ灏?,
    totalCount bigint   default 0                 null comment '褰撳墠绌洪棿涓嬬殑鍥剧墖鏁伴噺',
    userId     bigint                             not null comment '鍒涘缓鐢ㄦ埛 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '鍒涘缓鏃堕棿',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '缂栬緫鏃堕棿',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '鏇存柊鏃堕棿',
    isDelete   tinyint  default 0                 not null comment '鏄惁鍒犻櫎',
    -- 绱㈠紩璁捐
    index idx_userId (userId),        -- 鎻愬崌鍩轰簬鐢ㄦ埛鐨勬煡璇㈡晥鐜?
    index idx_spaceName (spaceName),  -- 鎻愬崌鍩轰簬绌洪棿鍚嶇О鐨勬煡璇㈡晥鐜?
    index idx_spaceLevel (spaceLevel) -- 鎻愬崌鎸夌┖闂寸骇鍒煡璇㈢殑鏁堢巼
) comment '绌洪棿' collate = utf8mb4_unicode_ci;

-- 淇敼绌洪棿琛紝澧炲姞绌洪棿绫诲瀷瀛楁
ALTER TABLE space
    ADD COLUMN spaceType int default 0 not null comment '绌洪棿绫诲瀷锛?-绉佹湁 1-鍥㈤槦';

CREATE INDEX idx_spaceType ON space (spaceType);

-- 绌洪棿鎴愬憳琛?
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '绌洪棿 id',
    userId     bigint                                 not null comment '鐢ㄦ埛 id',
    spaceRole  varchar(128) default 'viewer'          null comment '绌洪棿瑙掕壊锛歷iewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '鍒涘缓鏃堕棿',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '鏇存柊鏃堕棿',
    -- 绱㈠紩璁捐
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 鍞竴绱㈠紩锛岀敤鎴峰湪涓€涓┖闂翠腑鍙兘鏈変竴涓鑹?
    INDEX idx_spaceId (spaceId),                    -- 鎻愬崌鎸夌┖闂存煡璇㈢殑鎬ц兘
    INDEX idx_userId (userId)                       -- 鎻愬崌鎸夌敤鎴锋煡璇㈢殑鎬ц兘
) comment '绌洪棿鐢ㄦ埛鍏宠仈' collate = utf8mb4_unicode_ci;

-- 瀹℃牳娑堟伅琛?
CREATE TABLE review_message
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    user_id    BIGINT NOT NULL COMMENT '鎺ユ敹鐢ㄦ埛 ID',
    content    TEXT   NOT NULL COMMENT '娑堟伅鍐呭',
    status     TINYINT  DEFAULT 0 COMMENT '娑堟伅鐘舵€侊細0=鏈锛?=宸茶',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    read_at    DATETIME DEFAULT NULL COMMENT '闃呰鏃堕棿',

    INDEX idx_user_id (user_id)
) COMMENT ='瀹℃牳娑堟伅琛?;

# 涓哄鏍告満鍒跺鍔犻厤棰濊〃锛岀敤鎴蜂竴鍛ㄥ唴鐢宠瘔娆℃暟銆?
CREATE TABLE `user_appeal_quota`
(
    `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
    `user_id`         BIGINT   NOT NULL COMMENT '鐢ㄦ埛ID',
    `week_start_date` DATE     NOT NULL COMMENT '褰撳墠璁板綍鐨勮捣濮嬪懆鏃ユ湡(濡傚懆涓€)',
    `appeal_used`     INT      NOT NULL DEFAULT 0 COMMENT '宸蹭娇鐢ㄧ殑鐢宠瘔娆℃暟(鏈€澶т负2)',
    `updated_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    `created_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_week` (`user_id`, `week_start_date`),
    CONSTRAINT `chk_appeal_used` CHECK (`appeal_used` BETWEEN 0 AND 2)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='鐢ㄦ埛鐢宠瘔閰嶉琛?;

# 涓哄鏍告満鍒跺鍔犻粦鍚嶅崟鍜岃鍛婃鏁板瓧娈点€?
ALTER TABLE user
    ADD warning_quota  INT     DEFAULT 3 COMMENT '鍓╀綑璀﹀憡娆℃暟锛堥粯璁や负3锛屽噺鍒?鍗虫媺榛戯級',
    ADD is_blacklisted BOOLEAN DEFAULT FALSE COMMENT '鏄惁宸茶鎷夐粦';

ALTER TABLE picture
    MODIFY COLUMN reviewStatus INT DEFAULT 0 COMMENT '瀹℃牳鐘舵€侊紙鍙傝 PictureReviewStatusEnum锛?;

CREATE TABLE user_email
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL UNIQUE COMMENT '鎵€灞炵敤鎴稩D锛屽敮涓€ = 涓€涓敤鎴峰彧鑳界粦瀹氫竴涓偖绠?,
    email       VARCHAR(254) NOT NULL UNIQUE COMMENT '閭鍦板潃锛屽敮涓€ = 涓€涓偖绠卞彧鑳藉搴斾竴涓敤鎴?,
    is_verified BOOLEAN  DEFAULT FALSE COMMENT '鏄惁宸查獙璇?,
    status      TINYINT  DEFAULT 0 COMMENT '鐘舵€? 0-姝ｅ父, 1-鍐荤粨, 2-灞忚斀',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_email ON user_email(email);
CREATE INDEX idx_user_id ON user_email(user_id);

CREATE TABLE IF NOT EXISTS sr_task_result
(
    id              BIGINT                             NOT NULL COMMENT '主键',
    task_id         BIGINT                             NOT NULL COMMENT '任务ID',
    task_no         VARCHAR(64)                        NOT NULL COMMENT '任务号',
    user_id         BIGINT                             NOT NULL COMMENT '创建用户ID',
    space_id        BIGINT                             NULL COMMENT '空间ID（团队空间结果会写入）',
    biz_type        VARCHAR(16)                        NOT NULL COMMENT '业务类型（当前仅 image，预留 video）',
    model_name      VARCHAR(64)                        NULL COMMENT '模型名称',
    model_version   VARCHAR(32)                        NULL COMMENT '模型版本',
    output_file_key VARCHAR(512)                       NOT NULL COMMENT '输出文件 COS key',
    output_format   VARCHAR(32)                        NULL COMMENT '输出格式',
    output_size     BIGINT                             NULL COMMENT '输出大小（字节）',
    output_width    INT                                NULL COMMENT '输出宽度',
    output_height   INT                                NULL COMMENT '输出高度',
    duration_ms     BIGINT                             NULL COMMENT '视频时长（毫秒）',
    fps             DECIMAL(6, 2)                      NULL COMMENT '视频帧率',
    bitrate_kbps    INT                                NULL COMMENT '视频码率 kbps',
    codec           VARCHAR(64)                        NULL COMMENT '视频编码',
    cost_ms         BIGINT   DEFAULT 0                 NOT NULL COMMENT '处理耗时（毫秒）',
    attempt         INT      DEFAULT 1                 NOT NULL COMMENT '重试次数',
    trace_id        VARCHAR(64)                        NULL COMMENT '链路ID',
    extra_json      JSON                               NULL COMMENT '扩展元数据',
    is_delete       TINYINT  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_user_created (user_id, created_at DESC),
    KEY idx_space_created (space_id, created_at DESC),
    KEY idx_biz_created (biz_type, created_at DESC),
    KEY idx_trace_id (trace_id)
) COMMENT ='超分结果表' collate = utf8mb4_unicode_ci;



