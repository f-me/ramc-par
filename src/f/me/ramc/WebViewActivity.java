package f.me.ramc;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.webkit.WebView;

public class WebViewActivity extends Activity {

	public static final String HTML_FILE_NAME = "f.me.ramc.HTML_FILE_NAME"; 
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_web_view);
		
		Intent intent = getIntent();
	    String htmlFileName = intent.getStringExtra(WebViewActivity.HTML_FILE_NAME);
		
		WebView webView = (WebView) findViewById(R.id.webView);
		webView.loadUrl("file:///android_asset/" + htmlFileName);
	}

}
