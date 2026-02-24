# 图片超分任务 API 文档（前端对接）

## 1. 基础信息
- 服务基地址：`http://{host}:{port}/api`
- 模块前缀：`/sr/task`
- 鉴权要求：全部接口需要登录态（与现有系统登录态保持一致）
- 统一响应结构：

```json
{
  "code": 0,
  "data": {},
  "message": "ok"
}
```

`code = 0` 表示成功，非 `0` 表示失败。

## 2. 状态枚举
- `CREATED`
- `QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`

说明：当前创建任务后初始状态为 `QUEUED`。

## 3. 创建超分任务
- 方法：`POST`
- 路径：`/sr/task/create`
- 完整地址：`/api/sr/task/create`
- 请求体：`application/json`

### 3.1 请求参数
- `pictureId`：`Long`，可选。关联图片 ID。
- `inputFileKey`：`String`，可选。输入图片在对象存储中的 key。
- `scale`：`Integer`，可选。仅支持 `2` 或 `4`，默认 `4`。
- `modelName`：`String`，可选。默认 `RealESRGAN_x4plus`。
- `modelVersion`：`String`，可选。默认 `v1.0.0`。

参数约束：
- `pictureId` 与 `inputFileKey` 不能同时为空。
- 若传了 `inputFileKey`，优先使用 `inputFileKey`。

### 3.2 请求示例
```json
{
  "pictureId": 192837465001,
  "scale": 4,
  "modelName": "RealESRGAN_x4plus",
  "modelVersion": "v1.0.0"
}
```

### 3.3 成功响应示例
```json
{
  "code": 0,
  "data": 194857362910,
  "message": "ok"
}
```

`data` 为任务 ID（`taskId`）。

## 4. 查询任务详情
- 方法：`GET`
- 路径：`/sr/task/get/vo`
- 完整地址：`/api/sr/task/get/vo?id={taskId}`

### 4.1 Query 参数
- `id`：`Long`，必填，任务 ID，必须大于 0。

### 4.2 成功响应示例
```json
{
  "code": 0,
  "data": {
    "id": 194857362910,
    "taskNo": "SR202602241103120011",
    "userId": 10001,
    "pictureId": 192837465001,
    "inputFileKey": "picture/2026/02/24/demo.png",
    "outputFileKey": "sr/output/194857362910/demo_x4.png",
    "status": "SUCCEEDED",
    "progress": 100,
    "scale": 4,
    "modelName": "RealESRGAN_x4plus",
    "modelVersion": "v1.0.0",
    "attempt": 1,
    "costMs": 1187,
    "errorCode": null,
    "errorMsg": null,
    "traceId": "trace_5b0d5b6fd5c649f0a81d0f5b9f7b7abc",
    "createTime": "2026-02-24T11:03:12",
    "updateTime": "2026-02-24T11:03:15"
  },
  "message": "ok"
}
```

## 5. 分页查询我的超分任务
- 方法：`POST`
- 路径：`/sr/task/list/page/my/vo`
- 完整地址：`/api/sr/task/list/page/my/vo`
- 请求体：`application/json`

### 5.1 请求参数
- `current`：`Integer`，可选，页码，默认 `1`，必须大于 `0`。
- `pageSize`：`Integer`，可选，默认 `10`，最大 `50`。
- `id`：`Long`，可选，按任务 ID 精确筛选。
- `taskNo`：`String`，可选，按任务编号模糊匹配。
- `status`：`String`，可选，状态枚举值之一。

### 5.2 请求示例
```json
{
  "current": 1,
  "pageSize": 10,
  "status": "RUNNING"
}
```

### 5.3 成功响应示例
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 194857362910,
        "taskNo": "SR202602241103120011",
        "userId": 10001,
        "pictureId": 192837465001,
        "inputFileKey": "picture/2026/02/24/demo.png",
        "outputFileKey": null,
        "status": "RUNNING",
        "progress": 70,
        "scale": 4,
        "modelName": "RealESRGAN_x4plus",
        "modelVersion": "v1.0.0",
        "attempt": 1,
        "costMs": null,
        "errorCode": null,
        "errorMsg": null,
        "traceId": "trace_5b0d5b6fd5c649f0a81d0f5b9f7b7abc",
        "createTime": "2026-02-24T11:03:12",
        "updateTime": "2026-02-24T11:03:14"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1
  },
  "message": "ok"
}
```

## 6. 常见错误码
- `40000`：请求参数错误（如 `scale` 非 `2/4`、`pageSize > 50`、`current <= 0`）
- `40100`：未登录
- `40101`：无权限
- `40400`：资源不存在（如任务不存在、图片不存在）
- `50001`：操作失败（如消息投递失败）

## 7. 前端对接建议
1. 创建任务后使用返回的 `taskId` 轮询“查询任务详情”接口。
2. 终态判断：`SUCCEEDED` / `FAILED` / `CANCELLED`。
3. `SUCCEEDED` 时使用 `outputFileKey` 获取超分结果文件地址。
