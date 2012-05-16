package com.test.twitterclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;


/**
 * Authorize application to use users account
 * 
 * @author Andrei Sobolev
 *
 */
public class LoginTwitter extends Activity	{
	
	private WebView webView;
	
	@Override
	public void onCreate(Bundle savedInstanceState)	{
		super.onCreate(savedInstanceState);
		Intent i = this.getIntent();
		setContentView(R.layout.login);
		webView = (WebView) findViewById(R.id.login);
		webView.getSettings().setSupportZoom(true); // Add zoom support
		webView.getSettings().setBuiltInZoomControls(true); // Add zoom controls
		webView.setWebViewClient(new WebViewClient()	{
			@Override
			public void onLoadResource(WebView view, String url)	{
				if(url.startsWith("http://www.yoursite.com"))	{
					view.stopLoading();
					Intent i = new Intent();
					Bundle b = new Bundle();
					b.putString("url", url);
					i.putExtras(b);
					setResult(1, i);
					finish();
				}
			}
		});
		webView.getSettings().setJavaScriptEnabled(true);
		webView.loadUrl(i.getStringExtra("url"));
	}
	
	/**
	 * onDestroy method is called when Activity is destroyed (finish() method is called)
	 * We need to clear WebView cache, including files in local storage.
	 */
	@Override
	public void onDestroy()	{
		super.onDestroy();
		webView.clearCache(true);
	}

}