-- 释放座位 Lua 脚本（原子操作）
-- KEYS[1]: 可用座位池 Set Key (concert:seats:{id}:{tierId}:available)
-- KEYS[2]: 已锁定座位 Hash Key (concert:seats:{id}:{tierId}:locked)
-- ARGV[1..N]: 要释放的座位标签列表

for i = 1, #ARGV do
    local seat = ARGV[i]
    -- 从已锁定 Hash 中移除
    redis.call('HDEL', KEYS[2], seat)
    -- 归还到可用池
    redis.call('SADD', KEYS[1], seat)
end

return 1