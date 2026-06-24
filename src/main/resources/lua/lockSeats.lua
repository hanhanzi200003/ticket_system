-- 锁座 Lua 脚本（原子操作）
-- KEYS[1]: 可用座位池 Set Key (concert:seats:{id}:{tierId}:available)
-- KEYS[2]: 已锁定座位 Hash Key (concert:seats:{id}:{tierId}:locked)
-- ARGV[1]: 需要锁定的数量
-- ARGV[2]: 订单号

local quantity = tonumber(ARGV[1])
local available = redis.call('SCARD', KEYS[1])

-- 库存不足，返回 {"0", availableCount}
if available < quantity then
    return {"0", tostring(available)}
end

-- 原子弹出 N 个座位
local seats = redis.call('SPOP', KEYS[1], quantity)

-- 构建返回结果：{"1", seat1, seat2, ...}
local result = {"1"}
for _, seat in ipairs(seats) do
    -- 标记为已锁定
    redis.call('HSET', KEYS[2], seat, ARGV[2])
    table.insert(result, seat)
end

-- 设置锁定 Hash 10 分钟过期（兜底：即使 MQ/定时任务失效，Redis 也会自动清除锁定）
redis.call('EXPIRE', KEYS[2], 600)

return result
