# 超分结果查询 API 对接文档（当前仅图片）

## 1. 基础信息
- 服务前缀：`/api`
- 模块前缀：`/sr/task`
- 鉴权：需要登录态（与现有系统登录保持一致）
- 当前能力：仅支持图片超分结果（`bizType=image`）

统一响应结构：
```json
{
  "code": 0,
  "data": {},
  "message": "ok"
}
```

## 2. 查询我的超分结果（分页）

- 方法：`POST`
- 路径：`/sr/task/result/list/page/my/vo`
- 完整地址：`/api/sr/task/result/list/page/my/vo`
- Content-Type：`application/json`

### 2.1 请求参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| current | Integer | 否 | 页码，默认 `1`，必须 `>0` |
| pageSize | Integer | 否 | 每页大小，默认 `10`，最大 `50` |
| taskNo | String | 否 | 按任务号模糊查询 |
| bizType | String | 否 | 业务类型，当前仅支持 `image` |
| modelName | String | 否 | 按模型名精确筛选 |
| startTime | DateTime | 否 | 结果创建时间起始 |
| endTime | DateTime | 否 | 结果创建时间结束 |

时间范围校验：
- 当 `startTime` 和 `endTime` 同时传入时，`startTime <= endTime`

### 2.2 请求示例
```json
{
  "current": 1,
  "pageSize": 10,
  "bizType": "image",
  "taskNo": "SR20260224",
  "modelName": "RealESRGAN_x4plus"
}
```

### 2.3 成功响应示例
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1950012345678900000,
        "taskId": 1950012345678899999,
        "taskNo": "SR202602241610001234",
        "bizType": "image",
        "modelName": "RealESRGAN_x4plus",
        "modelVersion": "v1.0.0",
        "outputFileKey": "sr/output/1950012345678899999/demo_x4.png",
        "outputUrl": "https://your-cos-host/sr/output/1950012345678899999/demo_x4.png",
        "outputFormat": "png",
        "outputSize": null,
        "outputWidth": null,
        "outputHeight": null,
        "durationMs": null,
        "fps": null,
        "bitrateKbps": null,
        "codec": null,
        "costMs": 1187,
        "attempt": 1,
        "traceId": "trace_xxx",
        "createTime": "2026-02-24T16:10:03",
        "updateTime": "2026-02-24T16:10:03"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1
  },
  "message": "ok"
}
```

## 3. 字段说明（重点）

- `outputFileKey`：COS 对象 key
- `outputUrl`：可直接访问的完整 COS 地址（后端自动拼接）
- `durationMs/fps/bitrateKbps/codec`：为后续视频扩展预留，当前图片场景通常为 `null`

## 4. 常见错误码

- `40000`：参数错误（如 `pageSize > 50`、`current <= 0`、`bizType != image`、时间范围非法）
- `40100`：未登录
- `40101`：无权限
- `50000`：系统错误

## 5. 对接建议

1. 列表展示使用 `outputUrl` 直接渲染图片。
2. 保留 `outputFileKey`，用于后续下载、回源或服务端二次处理。
3. 查询建议默认传 `bizType=image`，避免后续扩展时行为歧义。

## 6. 查询团队空间超分结果（分页）

- 方法：`POST`
- 路径：`/sr/task/result/list/page/space/vo`
- 完整地址：`/api/sr/task/result/list/page/space/vo`
- Content-Type：`application/json`
- 权限：需为团队空间成员（`viewer/editor/admin`）或系统管理员

### 6.1 请求参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| spaceId | Long | 是 | 团队空间 ID |
| current | Integer | 否 | 页码，默认 `1`，必须 `>0` |
| pageSize | Integer | 否 | 每页大小，默认 `10`，最大 `50` |
| taskNo | String | 否 | 按任务号模糊查询 |
| modelName | String | 否 | 按模型名精确筛选 |
| startTime | DateTime | 否 | 结果创建时间起始 |
| endTime | DateTime | 否 | 结果创建时间结束 |

### 6.2 请求示例
```json
{
  "spaceId": 1945054164708327426,
  "current": 1,
  "pageSize": 10,
  "taskNo": "SR20260224"
}
```

### 6.3 说明

- 返回结果包含团队空间内所有成员创建的超分结果（当前仅图片）。
- 结果记录中会返回 `userId` 字段，用于前端展示创建者。
