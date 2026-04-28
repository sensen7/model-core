-- =============================================================
-- 余额预扣 Lua 脚本
-- 原子操作：检查余额 -> 扣减预估费用 -> 返回剩余余额
--
-- 为何用 Lua：Redis 单线程执行 Lua 脚本，天然保证原子性，
-- 避免高并发下"检查-扣减"之间出现竞态条件导致超扣。
--
-- 余额精度说明：为避免浮点误差，余额以 Long 存储（实际金额 × 10000）
-- 例如 $1.5000 存储为 15000
--
-- KEYS[1] = 余额 Key（格式：user:balance:{apiKeyId}）
-- ARGV[1] = 预估扣减金额（Long，已乘 10000）
--
-- 返回值：
--   >= 0  : 扣减成功，返回剩余余额
--   -1    : 余额不足
--   -2    : Key 不存在
-- =============================================================

local balance_key = KEYS[1]
local deduct_amount = tonumber(ARGV[1])

-- 检查余额 Key 是否存在
local exists = redis.call('EXISTS', balance_key)
if exists == 0 then
    return -2
end

-- 获取当前余额
local current_balance = tonumber(redis.call('GET', balance_key))

-- 检查余额是否充足
if current_balance < deduct_amount then
    return -1
end

-- 原子扣减
local new_balance = current_balance - deduct_amount
redis.call('SET', balance_key, new_balance)

return new_balance
