-- =============================================================
-- 二次确认扣费 Lua 脚本
-- 流式响应结束后，用精确 Token 数计算实际费用，调整预扣差额。
--
-- 逻辑：实际费用 - 预估费用 = 差额
--   差额 > 0：需要补扣（余额减少）
--   差额 < 0：需要退还（余额增加）
--   差额 = 0：无需操作
--
-- KEYS[1] = 余额 Key（格式：user:balance:{apiKeyId}）
-- ARGV[1] = 预估费用（Long，已乘 10000）
-- ARGV[2] = 实际费用（Long，已乘 10000）
--
-- 返回值：
--   >= 0  : 调整成功，返回最新余额
--   -1    : 补扣时余额不足（仍会扣至 0）
--   -2    : Key 不存在
-- =============================================================

local balance_key = KEYS[1]
local estimated_cost = tonumber(ARGV[1])
local actual_cost = tonumber(ARGV[2])

-- 检查余额 Key 是否存在
local exists = redis.call('EXISTS', balance_key)
if exists == 0 then
    return -2
end

local current_balance = tonumber(redis.call('GET', balance_key))

-- 计算差额：实际费用 - 预估费用
local diff = actual_cost - estimated_cost

if diff == 0 then
    -- 无需调整
    return current_balance
elseif diff > 0 then
    -- 需要补扣
    local new_balance = current_balance - diff
    if new_balance < 0 then
        -- 余额不足以补扣，扣至 0
        redis.call('SET', balance_key, 0)
        return -1
    else
        redis.call('SET', balance_key, new_balance)
        return new_balance
    end
else
    -- 需要退还（diff 为负数，减去负数等于加）
    local new_balance = current_balance - diff
    redis.call('SET', balance_key, new_balance)
    return new_balance
end
