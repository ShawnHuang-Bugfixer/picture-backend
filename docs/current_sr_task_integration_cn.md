# 当前超分任务对接说明

本文基于当前仓库实际代码整理，覆盖 `python_sr_service` 的图片/视频超分任务对接方式。本文以源码实现为准，不以历史设计文档为准。

## 1. 当前对接总体结构

当前链路如下：

```text
Java/上游业务
  -> 上传输入文件到 COS
  -> 发布 RabbitMQ 任务消息 sr.task
Python SR Worker
  -> 校验消息
  -> Redis 幂等检查
  -> 下载 COS 输入文件
  -> 执行图片或视频超分
  -> 上传结果到 COS
  -> 发布 RabbitMQ 结果消息 sr.result
  -> MySQL 记录事件流水
Java/上游业务
  -> 消费 sr.result
  -> 更新任务状态并保存 outputFileKey
```

对应代码位置：

- 服务入口：`python_sr_service/app.py`
- 配置加载：`python_sr_service/config.py`
- 任务消费：`python_sr_service/worker/consumer.py`
- 结果发布：`python_sr_service/worker/publisher.py`
- COS 适配：`python_sr_service/storage/cos_client.py`
- 图片超分：`python_sr_service/pipeline/image_pipeline.py`
- 视频超分：`python_sr_service/pipeline/video_pipeline.py`
- 消息协议：`python_sr_service/domain/schema.py`
- 幂等：`python_sr_service/idempotency/redis_store.py`
- 事件落库：`python_sr_service/persistence/mysql_event_repo.py`

## 2. 当前能力边界

当前实现不是仅图片模式，而是已经支持两类任务：

- `type=image`
- `type=video`

但有一个非常关键的真实约束：

- 服务端模型名被强制锁定为 `RealESRGAN_x4plus`
- 即使任务消息里传了别的 `modelName`，也只做记录和告警，不会按任务动态切换模型

这点由以下代码共同决定：

- `python_sr_service/app.py` 启动时把配置中的 `model_name` 替换为 `LOCKED_SERVICE_MODEL_NAME`
- `python_sr_service/worker/consumer.py` 中 `LOCKED_SERVICE_MODEL_NAME = 'RealESRGAN_x4plus'`
- `python_sr_service/worker/consumer.py` 中 `_resolve_model_name()` 会忽略任务内的 `modelName`

因此，当前对接口径应视为：

- `modelName` 是协议必填字段
- 但当前版本只接受并实际执行 `RealESRGAN_x4plus`
- 其他模型名不会生效

## 3. 基础组件与当前配置来源

服务依赖以下外部组件：

- RabbitMQ：任务投递与结果回传
- 腾讯 COS：输入下载与结果上传
- Redis：幂等去重
- MySQL：任务事件流水

配置来源顺序：

- 优先环境变量
- 其次 `python_sr_service/application.yml`

当前代码支持的主要配置类别：

- `cos`
- `mysql`
- `rabbitmq`
- `redis`
- `idempotency`
- `inference`
- `runtime`

建议对接时不要直接依赖配置文件中的具体凭证值，只依赖字段含义和环境覆盖机制。

## 4. RabbitMQ 拓扑

当前源码中的 MQ 拓扑固定如下。

### 4.1 任务侧

- Exchange：`x.sr.task.direct`
- Routing Key：`sr.task`
- Queue：`sr.task.queue`
- 消费模式：手动 ack
- `prefetch_count=1`

### 4.2 结果侧

- Exchange：`x.sr.result.direct`
- Routing Key：`sr.result`

说明：

- Python 服务只负责声明结果交换机
- Java 或其他上游需要自行绑定结果队列，例如 `sr.result.java.prod`

### 4.3 重试侧

- Exchange：`x.sr.retry.direct`
- 自动声明的延迟队列：
  - `sr.task.queue.retry.10s`
  - `sr.task.queue.retry.30s`
  - `sr.task.queue.retry.60s`

实现方式：

- Python 将可重试失败消息投递到重试交换机
- 重试队列通过 TTL 到期后死信回流主任务队列
- Java 端不需要直接操作重试交换机

## 5. 任务消息协议

当前任务消息由 `TaskMessage.from_dict()` 校验，必填字段如下：

```json
{
  "schemaVersion": "1.0",
  "eventId": "evt_task_xxx",
  "timestamp": "2026-03-22T01:23:45Z",
  "taskId": 100001,
  "taskNo": "SR202603220001",
  "userId": 20001,
  "type": "image",
  "inputFileKey": "input/2026/03/demo.png",
  "scale": 4,
  "modelName": "RealESRGAN_x4plus",
  "modelVersion": "v1.0.0",
  "attempt": 1,
  "traceId": "trace_xxx"
}
```

字段要求：

- `timestamp` 必须是 ISO-8601
- `type` 会被转成小写后比较，当前只支持 `image` 或 `video`
- `eventId` 用于幂等去重
- `attempt` 由上游维护
- `inputFileKey` 是 COS 中的对象 key

### 5.1 视频扩展字段

`type=video` 时，可额外携带：

```json
{
  "videoOptions": {
    "keepAudio": true,
    "extractFrameFirst": true,
    "fpsOverride": 23.976
  }
}
```

约束如下：

- `videoOptions` 可选
- `keepAudio` 默认 `true`
- `extractFrameFirst` 可选
  - `true`：强制抽帧模式
  - `false`：强制流式模式
  - 未传：按服务配置 `video_processing_mode` 决定
- `fpsOverride` 可选，必须大于 `0`

## 6. 结果消息协议

Python 回传结果消息格式如下：

```json
{
  "schemaVersion": "1.0",
  "eventId": "evt_result_xxx",
  "timestamp": "2026-03-22T01:23:58.123456+00:00",
  "taskId": 100001,
  "status": "SUCCEEDED",
  "progress": 100,
  "outputFileKey": "output/100001/SR202603220001_x4.png",
  "costMs": 12345,
  "attempt": 1,
  "errorCode": null,
  "errorMsg": null,
  "traceId": "trace_xxx"
}
```

状态含义：

- `RUNNING`：处理中，可能收到多次
- `SUCCEEDED`：处理完成
- `FAILED`：最终失败

当前实现没有主动发布 `CANCELLED`。

Java 侧建议：

- 不要假设一个任务只会收到一条结果消息
- 以 `SUCCEEDED` 或 `FAILED` 作为终态
- 结果消费按 `eventId` 去重

## 7. 真实执行时序

当前 `RabbitMQConsumer._on_message()` 的处理顺序如下：

1. 接收 RabbitMQ 消息
2. 反序列化 JSON
3. 校验任务字段
4. 校验 `type`
5. Redis 幂等检查，若已处理则直接 ack
6. MySQL 写入 `RECEIVED`
7. 发布 `RUNNING`，进度 `5`
8. 创建本地工作目录
9. 从 COS 下载输入文件
10. MySQL 写入 `DOWNLOADED`
11. 按 `image/video` 分发到不同 pipeline
12. 推理完成后上传结果到 COS
13. MySQL 写入 `UPLOADED`
14. 发布 `RUNNING`，进度 `95`
15. 发布 `SUCCEEDED`
16. MySQL 写入 `SUCCEEDED`
17. Redis 标记 `eventId` 已处理
18. ack 原消息
19. 清理工作目录

失败时：

- 可重试错误进入 `10s/30s/60s` 延迟重试
- 不可重试错误直接发布 `FAILED` 并 ack
- 最终失败也会写 MySQL 事件，并写入 Redis 幂等标记

## 8. 图片任务对接方式

图片任务处理逻辑：

1. 从 COS 下载原图到本地
2. OpenCV 读取图片
3. Real-ESRGAN 推理
4. OpenCV 写本地结果文件
5. 上传结果到 COS

输出路径规则：

- `output/{taskId}/{taskNo}_x{scale}{原扩展名}`

例如：

- 输入 `input/demo.png`
- 输出可能为 `output/100001/SR202603220001_x4.png`

补充说明：

- 输出扩展名跟随输入文件扩展名
- 图片推理内置显存不足 tile fallback 机制
- 当 `tile=0` 时会按图片大小自动选择初始 tile，并在 OOM 时继续降级

## 9. 视频任务对接方式

视频任务已经在当前代码中实现，不是设计态。

### 9.1 视频前置约束

服务会先执行 `ffprobe` 获取：

- 帧率
- 帧数
- 时长
- 分辨率
- 是否包含音频流

并按配置限制：

- `max_video_frames`
- `max_video_seconds`

超过限制会直接失败，错误码为 `VIDEO_LIMIT_EXCEEDED`。

### 9.2 视频处理模式

当前支持两种模式：

- `stream`
- `extract`

选择优先级：

1. 任务 `videoOptions.extractFrameFirst`
2. 服务配置 `video_processing_mode`

默认配置为：

- `video_processing_mode: stream`

### 9.3 stream 模式

处理链路：

1. `ffmpeg` 解码视频流为原始帧
2. 每帧进入图片超分逻辑
3. 将超分后的帧通过管道写回 `ffmpeg`
4. `ffmpeg` 编码输出 mp4
5. 如有音频且允许保留，则复用原音频流

若流式处理过程中遇到 `FFMPEG_ERROR`，当前实现会自动降级到 `extract` 模式重试。

### 9.4 extract 模式

处理链路：

1. `ffmpeg` 抽帧到 `_frames_in`
2. 每帧做图片超分
3. 输出到 `_frames_out`
4. `ffmpeg` 将帧序列重新合成为 mp4
5. 如有音频且允许保留，则复用原音频流

处理完成后会删除 `_frames_in` 与 `_frames_out` 临时目录。

### 9.5 视频编码器策略

当前配置支持：

- 主编码器：`video_codec`
- 候选降级编码器：`video_codec_fallbacks`

默认配置为：

- 主编码器：`h264_nvenc`
- 降级列表：`h264_mf,libx264,mpeg4`

执行时会先读取 `ffmpeg -encoders`，过滤当前机器实际可用编码器，再按顺序尝试。

### 9.6 音频策略

当前音频相关行为：

- `keepAudio=true` 且源视频含音频时，优先保留音频
- 若带音频合成失败，且 `audio_fallback_no_audio=true`，则自动降级为无音频输出

### 9.7 视频输出规则

视频输出固定为 mp4：

- `output/{taskId}/{taskNo}_x{scale}.mp4`

## 10. 进度消息口径

当前实现中的进度并不是任意值，而是比较固定：

### 10.1 图片任务

- `5`：任务已接收
- `95`：结果已上传
- `100`：终态成功或失败

说明：

- 图片任务当前没有在下载完成或推理中间持续推送更多细分进度

### 10.2 视频任务

- `5`：任务已接收
- `20`：视频探测完成
- `30`：抽帧完成或进入流式逐帧阶段
- `35~80`：逐帧超分过程中按帧数推进
- `90`：视频合成完成
- `95`：结果上传完成
- `100`：终态成功或失败

## 11. 幂等、重试与一致性

### 11.1 幂等

当前幂等只基于 `eventId`：

- Redis key 格式：`dedupe:{eventId}`
- TTL 默认 `259200` 秒，即 `72` 小时

注意：

- 服务在终态成功或最终失败后才标记幂等完成
- 这意味着处理中重投的同一 `eventId`，仍有机会再次进入执行
- 当前实现更偏向“终态去重”，不是“领取即占位”

### 11.2 重试

可重试错误会进入 3 级延迟队列：

- 第 1 次：10 秒
- 第 2 次：30 秒
- 第 3 次：60 秒

重试计数通过消息头维护：

- `x-retry-attempt`

### 11.3 MySQL 事件流水

当前每个任务会尽力写入 `sr_task_event`，常见事件包括：

- `RECEIVED`
- `DOWNLOADED`
- `VIDEO_PROBED`
- `FRAMES_EXTRACTED`
- `FRAMES_INFERRED`
- `VIDEO_MERGED`
- `AUDIO_FALLBACK`
- `INFERRED`
- `UPLOADED`
- `SUCCEEDED`
- `FAILED`

说明：

- MySQL 写失败不会中断主任务
- 代码会记录 `MYSQL_WRITE_FAILED` 日志告警，但任务仍继续

## 12. 错误码口径

当前源码中会出现的主要错误码如下：

- `SCHEMA_INVALID`
- `TYPE_NOT_SUPPORTED`
- `INPUT_NOT_FOUND`
- `INPUT_INVALID`
- `MODEL_NOT_FOUND`
- `VIDEO_LIMIT_EXCEEDED`
- `FFMPEG_ERROR`
- `COS_DOWNLOAD_FAILED`
- `COS_UPLOAD_FAILED`
- `INFER_RUNTIME_ERROR`
- `GPU_OOM`
- `MYSQL_WRITE_FAILED`
- `INTERNAL_ERROR`

对接建议：

- Java 侧必须落库 `errorCode` 和 `errorMsg`
- 业务重试应优先依赖 Python 结果终态，而不是自行猜测

## 13. 对接方最小接入步骤

如果 Java 或其他上游要按当前实现接入，最小步骤如下：

1. 将原始图片或视频上传到 COS
2. 获取对象 key，填入 `inputFileKey`
3. 按本文协议组装任务消息
4. 发布到 `x.sr.task.direct` + `sr.task`
5. 监听 `x.sr.result.direct` 上自己绑定的结果队列
6. 收到 `RUNNING` 更新进度
7. 收到 `SUCCEEDED` 后保存 `outputFileKey`
8. 收到 `FAILED` 后保存 `errorCode/errorMsg`

## 14. 当前对接注意事项

这里列出几个最容易误解的点：

1. 当前不是“按任务动态选模型”，而是“服务端固定模型”。
2. 当前已经支持视频任务，不再只是图片任务。
3. 视频默认优先走 `stream`，不是固定抽帧模式。
4. 幂等是“终态去重”，不是“消费前抢占去重”。
5. 结果交换机由 Python 声明，但结果队列需要 Java 自己绑定。
6. MySQL 事件落库是辅助链路，不是主流程强依赖。
7. 输出文件地址要以 `outputFileKey` 为准，不要由业务侧自行拼接猜测。

## 15. 建议的对接口径

如果要对外统一说明当前版本，建议直接采用下面这段描述：

> 当前超分服务通过 RabbitMQ 接收任务，通过腾讯 COS 读写输入输出文件，支持图片与视频两类任务。任务消息统一投递到 `x.sr.task.direct/sr.task`，结果统一从 `x.sr.result.direct/sr.result` 回传。当前服务端实际执行模型固定为 `RealESRGAN_x4plus`，`modelName` 字段暂不支持按任务动态切换。视频任务默认走流式处理，失败时会自动降级到抽帧模式。幂等以 `eventId` 为键，并在任务终态后写入 Redis 去重记录。
