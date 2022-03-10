local lib = require('CoronaLibrary'):new{name = 'plugin.amazon.iap', publisherId = 'com.coronalabs'}

lib.target = 'amazon'
lib.isActive = false
lib.canMakePurchases = false
lib.canloadProducts = false

local functions = {'init', 'purchase', 'restore', 'getUserId', 'getUserData', 'finishTransaction', 'loadProducts', 'isSandboxMode', 'verify'}

for i = 1, #functions do
    local f = functions[i]
    lib[f] = function()
        print('plugin.amazon.iap: ' .. f .. '() is not supported on this platform.')
        if f == 'isSandboxMode' then
            return true
        end
    end
end

return lib
