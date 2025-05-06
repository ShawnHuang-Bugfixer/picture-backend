-- KEYS[1] = refresh_token key
-- KEYS[2] = reverse_index key

local refreshDeleted = redis.call("DEL", KEYS[1])

if refreshDeleted == 1 then
    local reverseDeleted = redis.call("DEL", KEYS[2])
    if reverseDeleted == 1 then
        return 1
    end
end

return 0
