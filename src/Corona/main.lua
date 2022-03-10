local store = require('plugin.amazon.iap.v3')
local widget = require('widget')
local json = require('json')

-- The main store listener receives events when you make a purchase, restore or if Amazon decides it needs to send you an update
local function storeListener(event)
	print('storeListener event:')
	if not event.isError then
		print('transaction:', json.prettify(event.transaction))
	else
		print(json.prettify(event))
	end
	store.finishTransaction(event.transaction)
end

-- You must call init() before anything else
store.init(storeListener)
print('isSandboxMode():', store.isSandboxMode()) -- true if you are testing with App Tester
print('isActive:', store.isActive) -- true if the plugin was properly initialized

local _W, _H = display.actualContentWidth, display.actualContentHeight
local _CX = display.contentCenterX

local width = _W * 0.8
local size = _H * 0.1
local buttonFontSize = 16

local y = size * 0.5
local spacing = _H * 0.12

widget.newButton{
	x = _CX, y = y,
	width = width, height = size,
	label = 'getUserId()',
	fontSize = buttonFontSize,
	onRelease = function()
		print('getUserId():', store.getUserId())
	end
}

widget.newButton{
	x = _CX, y = y + spacing,
	width = width, height = size,
	label = 'getUserData()',
	fontSize = buttonFontSize,
	onRelease = function()
		store.getUserData(function(event)
			print('getUserData() event:')
			if not event.isError then
				print('userId:', event.userId)
				print('marketplace:', event.marketplace)
			else
				print(json.prettify(event))
			end
		end)
	end
}

widget.newButton{
	x = _CX, y = y + spacing * 2,
	width = width, height = size,
	label = 'loadProducts()',
	fontSize = buttonFontSize,
	onRelease = function()
		store.loadProducts({'234234'}, function(event)
			print('loadProducts() event:')
			if not event.isError then
				print('products:', json.prettify(event.products))
				print('invalidProducts:', json.prettify(event.invalidProducts))
			else
				print(json.prettify(event))
			end
		end)
	end
}

widget.newButton{
	x = _CX, y = y + spacing * 3,
	width = width, height = size,
	label = 'purchase() consumable',
	fontSize = buttonFontSize,
	onRelease = function()
		store.purchase('234234')
	end
}

widget.newButton{
	x = _CX, y = y + spacing * 4,
	width = width, height = size,
	label = 'purchase() entitlement',
	fontSize = buttonFontSize,
	onRelease = function()
		store.purchase('entitlement1')
	end
}

widget.newButton{
	x = _CX, y = y + spacing * 5,
	width = width, height = size,
	label = 'purchase() subscription',
	fontSize = buttonFontSize,
	onRelease = function()
		store.purchase('subscription1weekly')
	end
}

widget.newButton{
	x = _CX, y = y + spacing * 6,
	width = width, height = size,
	label = 'restore()',
	fontSize = buttonFontSize,
	onRelease = function()
		store.restore()
	end
}
widget.newButton{
	x = _CX, y = y + spacing * 7,
	width = width, height = size,
	label = 'verify()',
	fontSize = buttonFontSize,
	onRelease = function()
		store.verify(function(event)
			print('DRM:', json.prettify(event))
		end)
	end
}
