# 超分辨率处理模块架构设计（可落地版）

## 1. 目标与边界
- 目标：在现有 `picture-backend` 基础上，新增可扩展的图片/视频超分能力。
- 约束：业务服务（Java）不做推理计算，推理由独立 Python 服务承担。
- 原则：先做可运行 MVP，再逐步增强多模型、多队列、优先级和计费能力。

## 2. 总体架构
```text
[Client]
   |
   v
[Java API / 业务服务]
   | 1. 上传原文件到对象存储
   | 2. 创建任务记录（DB）
   | 3. 发布任务消息（RabbitMQ: sr.task）
   v
[RabbitMQ]
   |
   v
[Python 推理服务 (GPU)]
   | 1. 消费任务
   | 2. 下载输入文件（对象存储）
   | 3. 超分推理（图像/视频）
   | 4. 上传输出文件（对象存储）
   | 5. 发布结果消息（RabbitMQ: sr.result）
   v
[Java 结果消费者]
   | 1. 幂等更新任务状态（DB）
   | 2. 写通知消息
   | 3. WebSocket/SSE 推送
   v
[Client]
```

## 3. 组件职责
### 3.1 Java 业务服务（控制面）
- 鉴权、配额与参数校验。
- 任务创建、状态查询、取消任务。
- 维护任务状态机与重试计数。
- 消费推理结果并触发消息推送。

### 3.2 Python 推理服务（数据面）
- 从 `sr.task` 消费任务。
- 执行模型推理：
  - 图片：Real-ESRGAN。
  - 视频：`抽帧 -> 帧超分 -> 合成`。
- 回传结果消息到 `sr.result`。

### 3.3 对象存储（S3/MinIO）
- 输入文件、输出文件统一管理。
- 消息只传 `objectKey`，不传二进制。

### 3.4 RabbitMQ
- 解耦业务端和推理端。
- 提供重试、死信和削峰能力。

## 4. 任务状态机（统一口径）
状态：
- `QUEUED`：任务已入队
- `RUNNING`：推理执行中
- `SUCCEEDED`：成功
- `FAILED`：失败且超过重试上限
- `CANCELLED`：已取消

状态流转：
```text
CREATED -> QUEUED -> RUNNING -> SUCCEEDED
                          \-> FAILED
CREATED/QUEUED/RUNNING -> CANCELLED
```

## 5. 数据库模型建议
## 5.1 `sr_task`
- `id` bigint PK
- `task_no` varchar(64) unique
- `user_id` bigint not null
- `input_file_key` varchar(512) not null
- `output_file_key` varchar(512) null
- `task_type` varchar(16) not null (`image`/`video`)
- `scale` int not null
- `model_name` varchar(64) not null
- `model_version` varchar(32) not null
- `status` varchar(16) not null
- `progress` int default 0
- `retry_count` int default 0
- `max_retry` int default 3
- `error_code` varchar(64) null
- `error_msg` varchar(1024) null
- `trace_id` varchar(64) null
- `created_at` datetime
- `updated_at` datetime

索引建议：
- `idx_user_created(user_id, created_at desc)`
- `idx_status_created(status, created_at asc)`
- `uk_task_no(task_no)`

## 5.2 `sr_task_event`（可选，建议保留）
- `id` bigint PK
- `task_id` bigint not null
- `event_type` varchar(32) not null
- `event_payload` json/text
- `created_at` datetime

用途：审计、排障、回放。

## 6. RabbitMQ 拓扑建议
- 交换机：
  - `x.sr.task.direct`
  - `x.sr.result.direct`
  - `x.sr.retry.direct`
  - `x.sr.dlx.direct`
- 队列：
  - `sr.task.queue`
  - `sr.result.queue`
  - `sr.retry.queue`
  - `sr.dlq.queue`

路由：
- Java -> `x.sr.task.direct` -> `sr.task.queue`
- Python -> `x.sr.result.direct` -> `sr.result.queue`
- 失败重试 -> `x.sr.retry.direct` -> `sr.retry.queue`（TTL 到期后回投）
- 超过阈值 -> `x.sr.dlx.direct` -> `sr.dlq.queue`

## 7. 消息协议（建议）
## 7.1 任务消息 `sr.task`
```json
{
  "taskId": 123456,
  "taskNo": "SR20260214XXXX",
  "userId": 10001,
  "type": "image",
  "inputFileKey": "input/2026/02/a.png",
  "scale": 4,
  "modelName": "Real-ESRGAN",
  "modelVersion": "v1.0.0",
  "traceId": "9d2f..."
}
```

## 7.2 结果消息 `sr.result`
```json
{
  "taskId": 123456,
  "status": "SUCCEEDED",
  "progress": 100,
  "outputFileKey": "output/2026/02/a_x4.png",
  "costMs": 18320,
  "attempt": 1,
  "errorCode": null,
  "errorMsg": null,
  "traceId": "9d2f..."
}
```

## 8. 幂等与容错策略
- Java 创建任务时：先落库再发消息，避免“消息成功但无任务记录”。
- Python 消费时：按 `taskId + attempt` 幂等处理，重复消息不重复推理。
- Java 消费结果时：仅允许有效状态流转，拒绝旧结果覆盖新状态。
- 失败重试：指数退避（如 10s/30s/60s），超过 `max_retry` 标记 `FAILED`。
- 取消任务：若已 `RUNNING`，标记取消请求并由 Python 在阶段边界检查后退出。

## 9. 图片/视频执行策略
### 9.1 图片
- 单任务直接推理并上传结果。
- 进度：`10 -> 60 -> 90 -> 100`（下载/推理/上传/完成）。

### 9.2 视频
- 统一为单外部任务，内部阶段化：
  - `extract_frames`
  - `enhance_frames`
  - `merge_video`
- 对外只暴露一个 `taskId`，简化前端和业务系统。

## 10. 安全与治理
- 消息中不传签名密钥和临时凭证。
- 对象存储访问使用服务端凭证或短期 STS。
- 限制单用户并发任务数和单文件大小。
- 记录 `traceId/taskId/userId/modelVersion` 全链路日志。

## 11. 对现有项目的集成点
- 复用现有 RabbitMQ 配置模式（新增 SR 相关 exchange/queue 常量）。
- 复用现有消息推送链路（WebSocket/SSE + 离线消息表）。
- 复用现有鉴权与权限体系（仅允许登录用户创建 SR 任务）。

建议新增模块：
- `controller/SrTaskController.java`
- `service/SrTaskService.java`
- `service/impl/SrTaskServiceImpl.java`
- `service/messageconsumer/SrResultMessageConsumer.java`
- `model/entity/SrTask.java`
- `model/dto/sr/*`
- `model/vo/sr/*`

## 12. 实施里程碑
### Phase 1（MVP，图片）
- Java 创建任务 + 状态查询。
- RabbitMQ `sr.task/sr.result` 单队列。
- Python 图片超分跑通。

### Phase 2（稳定性）
- 重试队列 + DLQ + 幂等校验。
- 任务取消、失败可观测性、报警。

### Phase 3（视频与扩展）
- 视频全链路。
- 多模型路由、优先级队列、配额与计费。

