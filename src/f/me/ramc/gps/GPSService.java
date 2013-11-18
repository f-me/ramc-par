package f.me.ramc.gps;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageInfo;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import f.me.ramc.AdditionalKeyStoresSSLSocketFactory;
import f.me.ramc.MainActivity;
import f.me.ramc.R;

public class GPSService extends Service implements LocationListener,
	GooglePlayServicesClient.ConnectionCallbacks,
	GooglePlayServicesClient.OnConnectionFailedListener {

	private LocationRequest mLocationRequest;  
	private LocationClient mLocationClient;  

	public static final int MSG_SET_IS_FREE = 1;
	
	public static final int MSG_REGISTER_CLIENT = 2;
	
	public static final int MSG_UNREGISTER_CLIENT = 3;
	
	public static final int MSG_SET_LOCATION = 4;
	
	public static boolean isFree = true;
	
	/** Keeps track of all current registered clients. */
    private ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    
	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	
	public GPSService() {
		
	}
	
	@Override
	public void onCreate() {
		servicesConnected();
		
		mLocationRequest = LocationRequest.create();
		mLocationRequest.setInterval(LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		mLocationRequest.setFastestInterval(LocationUtils.FASTEST_INTERVAL_IN_MILLISECONDS);
		mLocationClient = new LocationClient(getApplicationContext(), this, this);
		mLocationClient.connect();
	}
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		Context context = getApplicationContext();
		Notification notification = new Notification(R.drawable.ic_notify,
				context.getString(R.string.app_name),
		        System.currentTimeMillis());
		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(this, context.getString(R.string.app_name),
				context.getString(R.string.partner_id), pendingIntent);
		startForeground(Integer.valueOf(context.getString(R.string.partner_id)), notification);
		
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_REDELIVER_INTENT;
    }
	
	/*
		Called when Sevice running in backgroung is stopped.
		Remove location upadate to stop receiving gps reading
	*/
	@Override
	public void onDestroy() {
		mLocationClient.removeLocationUpdates(this);
		super.onDestroy();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	/*
		Overriden method of interface LocationListener called when location of gps device is changed.
		Location Object is received as a parameter.
		This method is called when location of GPS device is changed
	*/
	public void onLocationChanged(Location location) {
		new SendToRAMC().execute(location);
		
		for (Messenger client : mClients) {
            try {
                client.send(Message.obtain(null,
                		MSG_SET_LOCATION, location));
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(client);
            }
        }
	}
	
    /*
     * Implementation of OnConnectionFailedListener.onConnectionFailed
     * If a connection or disconnection request fails, report the error
     * connectionResult is passed in from Location Services
     */
	public void onConnectionFailed(ConnectionResult connectionResult) {
		/*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {

            try {
                connectionResult.startResolutionForResult((Activity) getApplicationContext(),
                    LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

            /*
             * Thrown if Google Play services canceled the original
             * PendingIntent
             */
            } catch (SendIntentException e) {
               // display an error or log it here.
            }

        /*
         * If no resolution is available, display Google
         * Play service error dialog. This may direct the
         * user to Google Play Store if Google Play services
         * is out of date.
         */
        } else {
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                            connectionResult.getErrorCode(),
                            (Activity) getApplicationContext(),
                            LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);
            if (dialog != null) {
                dialog.show();
            }
        }
	}

	public void onConnected(Bundle connectionHint) {
		mLocationClient.requestLocationUpdates(mLocationRequest, this);
		
		// If debugging, log the connection
        Log.d(LocationUtils.APPTAG, getApplicationContext().getString(R.string.connected));
	}

	public void onDisconnected() {
		// In debug mode, log the disconnection
        Log.d(LocationUtils.APPTAG, getApplicationContext().getString(R.string.disconnected));
	}
	
	/**
     * Verify that Google Play services is available before making a request.
     *
     * @return true if Google Play services is available, otherwise false
     */
    private boolean servicesConnected() {

        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // Continue
            return true;

        // Google Play services was not available for some reason
        } else {
            // Display an error dialog
            GooglePlayServicesUtil.getErrorDialog(resultCode, (Activity) getApplicationContext(), 0).show();
            return false;
        }
    }
	
    private class SendToRAMC extends AsyncTask<Location, Void, String> {

		@Override
		protected String doInBackground(Location... locs) {
			try {
				// Create a KeyStore containing our trusted CAs
				KeyStore keystore = KeyStore.getInstance("PKCS12");
				keystore.load(getResources().openRawResource(R.raw.keystore), "".toCharArray());
				
				SSLSocketFactory sslSocketFactory = new AdditionalKeyStoresSSLSocketFactory(keystore);
				// use this for test servers
				//sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

				HttpParams params = new BasicHttpParams();
		        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
		        HttpProtocolParams.setUseExpectContinue(params, true);
		        
		        PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				String userAgent = "ramc-par" + "/" + pInfo.versionName +
									" (rv:" + String.valueOf(pInfo.versionCode) + "; " + "Android)";
		        HttpProtocolParams.setUserAgent(params, userAgent);

		        final SchemeRegistry registry = new SchemeRegistry();
		        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		        registry.register(new Scheme("https", sslSocketFactory, 40444));
		        
				ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, registry);
				DefaultHttpClient httpclient = new DefaultHttpClient(manager, params);

				String partnerId = getResources().getString(R.string.partner_id);
		    	String url = getResources().getString(R.string.ramc_url) + partnerId;
				HttpPut put = new HttpPut(url);

				Location loc = locs[0];
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
		        nameValuePairs.add(new BasicNameValuePair(
		        		"lon",String.format(Locale.US, "%.7f", loc.getLongitude())));
		        nameValuePairs.add(new BasicNameValuePair(
		        		"lat",String.format(Locale.US, "%.7f", loc.getLatitude())));
		        nameValuePairs.add(new BasicNameValuePair(
		        		"isFree",String.valueOf(isFree)));
	        	put.setEntity(new UrlEncodedFormEntity(nameValuePairs));
	        	
	            ResponseHandler<String> responseHandler = new BasicResponseHandler();
	            httpclient.execute(put, responseHandler);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return locs[0].toString();
		}
    	
		@Override
		protected void onPostExecute(String msg) {
			Log.d(LocationUtils.APPTAG, msg);
		}
    }
	
    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
	            case MSG_REGISTER_CLIENT:
	                mClients.add(msg.replyTo);
	                break;
	            case MSG_UNREGISTER_CLIENT:
	                mClients.remove(msg.replyTo);
	                break;
                case MSG_SET_IS_FREE:
                    isFree = msg.arg1 > 0;
                    for (Messenger client : mClients) {
                        try {
                            client.send(Message.obtain(null,
                            		MSG_SET_IS_FREE, isFree ? 1 : 0, 0));
                        } catch (RemoteException e) {
                            // The client is dead.  Remove it from the list;
                            // we are going through the list from back to front
                            // so this is safe to do inside the loop.
                            mClients.remove(client);
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
