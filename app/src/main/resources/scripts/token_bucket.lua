-- 原子化多维度令牌桶限流脚本
-- 与 rate_limit.lua（滑动窗口）保持同风格：多维度、两阶段（先预检查后统一扣减）、Lua 原子执行
-- 每个维度一个 HASH key：tokens = 当前令牌数，ts = 上次补充令牌的时间戳（毫秒）
-- 按经过时间以固定速率惰性补充令牌（每 interval/capacity 毫秒补充 1 个），上限 capacity
-- 只有所有维度都满足条件时才扣减令牌，确保原子性

-- 参数说明：
-- KEYS[1..N]: 限流维度键列表
-- ARGV[1]: 当前时间戳（毫秒）
-- ARGV[2]: 申请令牌数
-- ARGV[3]: 时间窗口（毫秒），即每个时间窗口补充 capacity 个令牌
-- ARGV[4]: 桶容量（窗口内允许的总请求数，即峰值突发量）
-- ARGV[5]: 请求唯一标识（保留参数，与滑动窗口脚本签名对齐，本脚本未使用）

local now_ms = tonumber(ARGV[1])
local permits = tonumber(ARGV[2])
local interval = tonumber(ARGV[3])
local capacity = tonumber(ARGV[4])

-- 每补充 1 个令牌需要的毫秒数
local refill_interval = interval / capacity

-- key 过期时间：2 倍窗口（至少 1 秒），保证空闲桶自动清理
local expire_time = math.ceil(interval * 2 / 1000)
if expire_time < 1 then expire_time = 1 end

-- 惰性补充令牌：按距离上次补充的经过时间补充令牌，返回补充后的可用令牌数
local function refill_tokens(key)
    local tokens = tonumber(redis.call("hget", key, "tokens"))
    local ts = tonumber(redis.call("hget", key, "ts"))

    -- 首次访问：初始化为满桶
    if tokens == nil or ts == nil then
        redis.call("hset", key, "tokens", capacity, "ts", now_ms)
        redis.call("expire", key, expire_time)
        return capacity
    end

    local elapsed = now_ms - ts
    if elapsed <= 0 then
        return tokens
    end

    local added = math.floor(elapsed / refill_interval)
    if added > 0 then
        tokens = math.min(capacity, tokens + added)
        redis.call("hset", key, "tokens", tokens, "ts", now_ms)
        redis.call("expire", key, expire_time)
    end
    return tokens
end

-- 第一阶段：预检查阶段 - 补充并检查所有维度的令牌是否充足
for i, key in ipairs(KEYS) do
    local tokens = refill_tokens(key)
    if tokens < permits then
        -- 任何一个维度令牌不足，直接返回失败（不扣减，其他维度状态保持不变）
        return 0
    end
end

-- 第二阶段：扣减阶段 - 所有维度都通过后统一扣减
-- （同一 Lua 脚本原子执行，两个阶段之间不存在并发干扰，无需二次补充）
for i, key in ipairs(KEYS) do
    local tokens = tonumber(redis.call("hget", key, "tokens")) - permits
    redis.call("hset", key, "tokens", tokens)
    redis.call("expire", key, expire_time)
end

-- 成功获取所有维度的令牌
return 1
