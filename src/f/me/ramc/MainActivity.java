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
import f.me.ramc.gps.LocationUtils;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;


public class MainActivity extends Activity {
	
	public static final String RAMC_PAR_PREFS = "RAMC_PAR_PREFS";
	
	private MapView mMapView;
	private GoogleMap mMap;
	
	private boolean mIsBound = false;
	private Messenger mService = null;
	private ToggleButton mIsFreeBtn;
	
	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	
	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  We are communicating with our
	        // service through an IDL interface, so get a client-side
	        // representation of that from the raw service object.
	        mService = new Messenger(service);

	        // We want to monitor the service for as long as we are
	        // connected to it.
	        try {
	        	Message msg = Message.obtain(null,
	                    GPSService.MSG_REGISTER_CLIENT);
	            msg.replyTo = mMessenger;
	            mService.send(msg);
	            
	            // Give it some value as an example.
	            msg = Message.obtain(null,
	                    GPSService.MSG_SET_IS_FREE, mIsFreeBtn.isChecked() ? 1 : 0, 0);
	            msg.replyTo = mMessenger;
	            mService.send(msg);
	        } catch (RemoteException e) {
	            // In this case the service has crashed before we could even
	            // do anything with it; we can count on soon being
	            // disconnected (and then reconnected if it can be restarted)
	            // so there is no need to do anything here.
	        }
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        mService = null;
	    }
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        Intent pushIntent = new Intent(this, GPSService.class);  
		startService(pushIntent);
        
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
                    break;
                }

            // If any other request code was received
            default:
               // Report that this Activity received an unknown requestCode
               Log.d(LocationUtils.APPTAG,
                       getString(R.string.unknown_activity_request_code, requestCode));

               break;
        }
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
        doBindService();
    }
    
    @Override
    public void onPause() {
    	doUnbindService();
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
    	try {
            Message msg = Message.obtain(null,
                    GPSService.MSG_SET_IS_FREE, mIsFreeBtn.isChecked() ? 1 : 0, 0);
            if (mService != null) {
            	mService.send(msg);
            }
        } catch (RemoteException e) {
            // In this case the service has crashed before we could even
            // do anything with it; we can count on soon being
            // disconnected (and then reconnected if it can be restarted)
            // so there is no need to do anything here.
        }
    }
    
    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        bindService(new Intent(this, 
                GPSService.class), mConnection, Context.BIND_IMPORTANT);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
        	// If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null,
                            GPSService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }
        	
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_info:
            	Intent intent = new Intent(this, InfoActivity.class);
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
	
	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
	    @Override
	    public void handleMessage(Message msg) {
	        switch (msg.what) {
	            case GPSService.MSG_SET_IS_FREE:
	            	mIsFreeBtn.setChecked(msg.arg1 > 0);
	                break;
	            case GPSService.MSG_SET_LOCATION:
	            	Location loc = (Location) msg.obj;
	            	mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(loc.getLatitude(), loc.getLongitude())));
	                break;
	            default:
	                super.handleMessage(msg);
	        }
	    }
	}
	
}
