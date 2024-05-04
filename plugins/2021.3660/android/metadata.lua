local metadata = {
	plugin = {
		format = 'jar',
		manifest =  {
		applicationChildElements = {
[[	<receiver android:name = "com.amazon.device.iap.ResponseReceiver"
	android:permission = "com.amazon.inapp.purchasing.Permission.NOTIFY" android:exported="true">
	<intent-filter>
	<action android:name = "com.amazon.inapp.purchasing.NOTIFY" />
	</intent-filter>
</receiver>
<receiver android:name = "com.amazon.device.drm.ResponseReceiver" android:exported="true"
	android:permission = "com.amazon.drm.Permission.NOTIFY" >
	<intent-filter>
		<action android:name = "com.amazon.drm.NOTIFY" />
	</intent-filter>
</receiver>
]]
		},
		}
	}
}

return metadata
