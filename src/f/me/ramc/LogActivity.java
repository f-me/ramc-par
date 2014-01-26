package f.me.ramc;

import f.me.ramc.gps.GPSService;
import f.me.ramc.gps.GPSServiceConnection;
import f.me.ramc.gps.IGPSService;
import f.me.ramc.gps.LocationUtils;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class LogActivity extends Activity {

	private TextView mLastSuccess;
	private TextView mResponseLog;
	private GPSServiceConnection gpsServiceConnection;
	
	private final Runnable bindChangedCallback = new Runnable() {                 
		@Override                                                                 
		public void run() {                                                         
			// After binding changes (is available), update the Partner status
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					IGPSService gpsService = gpsServiceConnection.getServiceIfBound();
			    	if (gpsService != null) {
			    		try {
			    			mLastSuccess.setText(gpsService.getLastSuccessfulRequest());
						} catch (RemoteException e) {
							Log.d(LocationUtils.APPTAG, e.getLocalizedMessage());
						}
			    	}
				}
			});
		}
	};
	
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			// Extract data included in the Intent
			String responseString = intent.getStringExtra(GPSService.LOCATION_UPDATE_STATE);
			mResponseLog.setText(responseString + "\n\n" + mResponseLog.getText());
			// check last successful request data
			bindChangedCallback.run();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		mLastSuccess = (TextView) findViewById(R.id.lastSuccessTextView);
		mResponseLog = (TextView) findViewById(R.id.logTextView);
		mResponseLog.setMovementMethod(new ScrollingMovementMethod());
		
		gpsServiceConnection = new GPSServiceConnection(this, bindChangedCallback);
	}
	
	@Override
    public void onStart() {
    	super.onStart();
    	gpsServiceConnection.startAndBind();
    }
    
    @Override
    public void onStop() {
    	gpsServiceConnection.unbind();
        super.onStop();
    }
	
    @Override
    public void onResume() {
    	super.onResume();
        registerReceiver(mMessageReceiver, new IntentFilter(GPSService.BROADCAST_LAST_LOCATION_UPDATE_INTENT));
    }
    
    @Override
    public void onPause() {
        unregisterReceiver(mMessageReceiver);
        super.onPause();
    }
}
