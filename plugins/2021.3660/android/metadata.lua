local metadata = {
	plugin = {
		format = 'jar',
		manifest =  {
			applicationChildElements = {
[[<receiver android:name = "com.amazon.device.iap.ResponseReceiver"
      android:permission = "com.amazon.inapp.purchasing.Permission.NOTIFY" android:exported="true">
    <intent-filter>
      <action android:name = "com.amazon.inapp.purchasing.NOTIFY" />
    </intent-filter>
  </receiver>]]
			}
		}
	}
}

return metadata
