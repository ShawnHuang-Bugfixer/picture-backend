# Python 超分服务 Java 视频超分对接文档

## 1. 文档目的
- 面向 Java 业务端，定义视频超分任务的投递协议、结果消费协议与联调规范。
- 对齐当前仓库实现（`python_sr_service`），适用于 `type=video` 任务。

## 2. 总体链路
1. Java 上传源视频到 COS，得到 `inputFileKey`。
2. Java 发布 `sr.task` 消息到 RabbitMQ。
3. Python Worker 消费后执行：下载 -> 抽帧 -> 超分 -> 合成 -> 上传。
4. Python 持续发布 `sr.result`（`RUNNING` + 终态）。
5. Java 消费结果并更新业务状态。

## 3. RabbitMQ 拓扑

### 3.1 任务侧（Java -> Python）
- Exchange: `x.sr.task.direct`
- RoutingKey: `sr.task`
- Queue: `sr.task.queue`
- 消息建议：`content_type=application/json`、`delivery_mode=2`

### 3.2 结果侧（Python -> Java）
- Exchange: `x.sr.result.direct`
- RoutingKey: `sr.result`
- Java 侧建议绑定独立结果队列：`sr.result.java.{env}`

### 3.3 重试侧（Python 内部）
- Exchange: `x.sr.retry.direct`
- 队列（自动声明）：
  - `sr.task.queue.retry.10s`
  - `sr.task.queue.retry.30s`
  - `sr.task.queue.retry.60s`

## 4. 任务消息协议（视频）

### 4.1 JSON 示例
```json
{
  "schemaVersion": "1.0",
  "eventId": "evt_task_9e3f5d36f1bb4e2f8f4ca65a9f8d1021",
  "timestamp": "2026-03-04T05:21:00Z",
  "taskId": 1772601597,
  "taskNo": "SR1772601597",
  "userId": 20001,
  "type": "video",
  "inputFileKey": "input/video/2026/03/demo.mp4",
  "scale": 2,
  "modelName": "RealESRGAN_x4plus",
  "modelVersion": "v1.0.0",
  "attempt": 1,
  "traceId": "trace_3fd9b4ce18e0455e8d64f8a9e6a7d1b2",
  "videoOptions": {
    "keepAudio": true,
    "extractFrameFirst": true,
    "fpsOverride": 23.976
  }
}
```

### 4.2 必填字段
- `schemaVersion`
- `eventId`
- `timestamp`（ISO-8601）
- `taskId`
- `taskNo`
- `userId`
- `type`（必须为 `video`）
- `inputFileKey`
- `scale`
- `modelName`
- `modelVersion`
- `attempt`
- `traceId`

### 4.3 videoOptions 字段规则
- `videoOptions` 可选，省略时使用默认值。
- `keepAudio`: bool，默认 `true`
- `extractFrameFirst`: bool，默认 `true`
- `fpsOverride`: number，可选，必须 `>0`

### 4.4 字段校验注意
- `type` 在 Python 侧会转小写比较，建议 Java 固定发 `video`。
- `timestamp` 必须是合法 ISO 时间。
- `eventId` 必须全局唯一（建议 UUID）。

## 5. 结果消息协议

### 5.1 JSON 示例（终态成功）
```json
{
  "schemaVersion": "1.0",
  "eventId": "evt_result_189533bdcbd04bff9a5b8fe00c92ebf1",
  "timestamp": "2026-03-04T05:21:03.447732+00:00",
  "taskId": 1772601597,
  "status": "SUCCEEDED",
  "progress": 100,
  "outputFileKey": "output/1772601597/SR1772601597_x2.mp4",
  "costMs": 64826,
  "attempt": 1,
  "errorCode": null,
  "errorMsg": null,
  "traceId": "trace_3fd9b4ce18e0455e8d64f8a9e6a7d1b2"
}
```

### 5.2 状态语义
- `RUNNING`: 处理中（会收到多次）
- `SUCCEEDED`: 成功完成
- `FAILED`: 最终失败
- `CANCELLED`: 预留状态（当前一般不会主动发）

### 5.3 进度区间（视频任务）
- `5`: 已接收
- `20`: 视频元信息探测完成
- `30`: 抽帧完成
- `35~80`: 帧超分中
- `90`: 合成完成
- `95`: 上传完成
- `100`: 终态（成功/失败）

## 6. 输出与存储约定
- 视频输出固定为 `.mp4`。
- 输出 key 规则：`output/{taskId}/{taskNo}_x{scale}.mp4`
- Java 侧应以 `outputFileKey` 作为最终下载定位，不要自行拼接路径。

## 7. 编码兼容性说明（重点）

### 7.1 当前策略
- 首选编码器：`VIDEO_CODEC`（默认 `libx264`）
- 自动降级：`VIDEO_CODEC_FALLBACKS`（默认 `h264_mf,mpeg4`）
- 执行前会按 `ffmpeg -encoders` 过滤当前机器可用编码器，再按顺序尝试。

### 7.2 建议
- Windows/Conda 环境推荐保持：`video_codec_fallbacks: h264_mf,mpeg4`
- 若播放器出现“只有音频无画面”，优先检查视频流编码：
```bash
ffprobe -v error -show_streams your_output.mp4
```
- Java 侧对外播放建议使用支持 H.264 的播放器/转码链路。

## 8. 错误码对接建议
- `SCHEMA_INVALID`: 消息字段或类型不合法
- `TYPE_NOT_SUPPORTED`: type 不支持或视频能力关闭
- `INPUT_NOT_FOUND`: COS 输入对象不存在
- `INPUT_INVALID`: 输入文件损坏/无视频流
- `VIDEO_LIMIT_EXCEEDED`: 超过帧数/时长限制
- `FFMPEG_ERROR`: ffmpeg/ffprobe 失败
- `COS_DOWNLOAD_FAILED`: 下载失败
- `COS_UPLOAD_FAILED`: 上传失败
- `INFER_RUNTIME_ERROR`: 推理阶段异常
- `GPU_OOM`: 显存不足

Java 端建议：
1. `FAILED` 必须落库 `errorCode/errorMsg`。
2. 根据 `errorCode` 区分业务重试和人工介入。

## 9. Java 发布示例（Spring AMQP）
```java
rabbitTemplate.convertAndSend(
    "x.sr.task.direct",
            "sr.task",
    taskJson,
    msg -> {
        msg.getMessageProperties().setContentType("application/json");
        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return msg;
    }
            );
```

## 10. Java 消费示例（结果）
```java
@RabbitListener(queues = "sr.result.java.prod")
public void onResult(String body) {
    ResultMessage msg = parse(body);

    switch (msg.getStatus()) {
        case "RUNNING":
            updateProgress(msg.getTaskId(), msg.getProgress());
            break;
        case "SUCCEEDED":
            markSuccess(msg.getTaskId(), msg.getOutputFileKey(), msg.getCostMs());
            break;
        case "FAILED":
            markFailed(msg.getTaskId(), msg.getErrorCode(), msg.getErrorMsg());
            break;
        default:
            log.warn("unknown status {}", msg.getStatus());
    }
}
```

## 11. 幂等与一致性建议
- 发布幂等：`eventId` 全局唯一。
- 消费幂等：Java 结果消费者按 `eventId` 或 `taskId+status+attempt` 去重。
- 任务完结条件：仅 `SUCCEEDED/FAILED`。
- `RUNNING` 仅用于进度展示，不要触发终态业务动作。

## 12. 联调检查清单
1. RabbitMQ 交换机/路由/队列与本文一致。
2. Java 上传到 COS 的 `inputFileKey` 可被 Python 读到。
3. `weights/` 下存在任务所需模型权重。
4. Java 能持续收到 `RUNNING` 与终态。
5. 成功时 `outputFileKey` 指向 mp4，且 `ffprobe` 有视频流。
6. 失败时 Java 已记录 `errorCode/errorMsg`。

## 13. 版本与兼容
- 当前协议版本：`schemaVersion=1.0`
- 后续新增字段应保持向后兼容：新增可选字段，不删除现有字段。
