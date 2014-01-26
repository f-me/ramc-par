package f.me.ramc;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.LatLng;

import f.me.ramc.gps.GPSService;
import f.me.ramc.gps.GPSServiceConnection;
import f.me.ramc.gps.IGPSService;
import f.me.ramc.gps.LocationUtils;
import android.location.Location;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;


public class MainActivity extends Activity {
	
	public static final String RAMC_PAR_PREFS = "RAMC_PAR_PREFS";
	
	private MapView mMapView;
	private GoogleMap mMap;
	private ToggleButton mIsFreeBtn;
	
	private GPSServiceConnection gpsServiceConnection;

	private final Runnable bindChangedCallback = new Runnable() {                 
		@Override                                                                 
		public void run() {                                                         
			// After binding changes (is available), update the Partner status
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					setFree(mIsFreeBtn.isChecked());
				}
			});
		}
	};

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			// Extract data included in the Intent
			Location loc = (Location) intent.getParcelableExtra(GPSService.LOCATION);
			mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(loc.getLatitude(), loc.getLongitude())));
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        gpsServiceConnection = new GPSServiceConnection(this, bindChangedCallback);
        
        // Needs to call MapsInitializer before doing any CameraUpdateFactory calls
 		try {
 			MapsInitializer.initialize(this);
 		} catch (GooglePlayServicesNotAvailableException e) {
 			e.printStackTrace();
 		}
 		
 		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
 		if (resultCode == ConnectionResult.SUCCESS) {
	        // Gets the MapView from the XML layout and creates it
	 		mMapView = (MapView) findViewById(R.id.map);
	 		mMapView.onCreate(savedInstanceState);
	  
	 		// Gets to GoogleMap from the MapView and does initialization stuff
	 		mMap = mMapView.getMap();
	 		mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
	 		mMap.getUiSettings().setAllGesturesEnabled(true);
	 		mMap.getUiSettings().setCompassEnabled(true);
	 		mMap.getUiSettings().setZoomControlsEnabled(true);
	 		mMap.setMyLocationEnabled(true);
 		} else {
 			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);
 	        dialog.setCancelable(false);
 	        dialog.show();
 		}
 		
 		mIsFreeBtn = (ToggleButton) findViewById(R.id.isFreeToggleButton);
        mIsFreeBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            	setFree(isChecked);
            }
        });
    }
    
    /*
     * Handle results returned to this Activity by other Activities started with
     * startActivityForResult(). In particular, the method onConnectionFailed() in
     * LocationUpdateRemover and LocationUpdateRequester may call startResolutionForResult() to
     * start an Activity that handles Google Play services problems. The result of this
     * call returns here, to onActivityResult.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Choose what to do based on the request code
        switch (requestCode) {

            // If the request code matches the code sent in onConnectionFailed
            case LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :

                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:
                        // Log the result
                        Log.d(LocationUtils.APPTAG, getString(R.string.resolved));
                        break;

                    // If any other result was returned by Google Play services
                    default:
                        // Log the result
                        Log.d(LocationUtils.APPTAG, getString(R.string.no_resolution));
                }

            // If any other request code was received
            default:
               // Report that this Activity received an unknown requestCode
               Log.d(LocationUtils.APPTAG,
                       getString(R.string.unknown_activity_request_code, requestCode));
        }
    }
    
    protected void onStart() {
    	super.onStart();
    	gpsServiceConnection.startAndBind();
    }
    
    protected void onStop() {
    	super.onStop();
    	gpsServiceConnection.unbind();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	if (mMapView != null) {
    		mMapView.onResume();
    	}
    	// Restore preferences
        SharedPreferences settings = getSharedPreferences(RAMC_PAR_PREFS, 0);
        mIsFreeBtn.setChecked(settings.getBoolean("isFree", true));
        
        registerReceiver(mMessageReceiver, new IntentFilter(GPSService.BROADCAST_LOCATION_INTENT));
    }
    
    @Override
    public void onPause() {
        unregisterReceiver(mMessageReceiver);
        // Save preferences
    	SharedPreferences settings = getSharedPreferences(RAMC_PAR_PREFS, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("isFree", mIsFreeBtn.isChecked());
        editor.commit();
        if (mMapView != null) {
        	mMapView.onPause();
        }
        super.onPause();
    }
    
    public void setFree(boolean isFree) {
    	IGPSService gpsService = gpsServiceConnection.getServiceIfBound();
    	if (gpsService != null) {
    		try {
				gpsService.setFree(isFree);
			} catch (RemoteException e) {
				Log.d(LocationUtils.APPTAG, e.getLocalizedMessage());
			}
    	}
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
    	Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_info:
            	intent = new Intent(this, InfoActivity.class);
            	startActivity(intent);
                return true;
            case R.id.menu_log:
            	intent = new Intent(this, LogActivity.class);
            	startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
}
