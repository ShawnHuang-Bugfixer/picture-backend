-- 参数说明：
-- KEYS[1]: 反向索引的 key（user_refresh_token:{userId}）
-- KEYS[2]: refresh_token 的 key 前缀（refresh_token:）

-- 1. 从反向索引中获取 refresh_token
local refreshToken = redis.call('GET', KEYS[1])

-- 2. 如果 refresh_token 存在，则删除 refresh_token 和反向索引
if refreshToken then
    local refreshKey = KEYS[2] .. refreshToken
    redis.call('DEL', refreshKey)  -- 删除 refresh_token
    redis.call('DEL', KEYS[1])     -- 删除反向索引
    return 1  -- 成功
else
    return 0  -- 反向索引不存在
end