package com.ariforlife.app
import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.webkit.*
import android.view.ViewGroup
class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val web=WebView(this)
  web.layoutParams=ViewGroup.LayoutParams(-1,-1)
  web.settings.javaScriptEnabled=true
  web.settings.domStorageEnabled=true
  web.settings.mediaPlaybackRequiresUserGesture=false
  web.webChromeClient=object:WebChromeClient(){override fun onPermissionRequest(r:PermissionRequest){runOnUiThread{r.grant(r.resources)}}}
  web.webViewClient=WebViewClient()
  setContentView(web)
  if(android.os.Build.VERSION.SDK_INT>=23) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10)
  web.loadUrl("https://ari900630-tech.github.io/Agents-for-life/")
 }
}
