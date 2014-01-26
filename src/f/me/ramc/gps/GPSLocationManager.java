package f.me.ramc.gps;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class GPSLocationManager {

	/**
	 * Observer for Google location settings.
	 */
	private class GoogleSettingsObserver extends ContentObserver {

		public GoogleSettingsObserver(Handler handler) {
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange) {
			isAllowed = isUseLocationForServicesOn();
		}
	}

	private static final String TAG = GPSLocationManager.class.getSimpleName();

	private static final String GOOGLE_SETTINGS_CONTENT_URI = "content://com.google.settings/partner";
	private static final String USE_LOCATION_FOR_SERVICES = "use_location_for_services";

	private static final String ACTION_GOOGLE_APPS_LOCATION_SETTINGS = "com.google.android.gsf.GOOGLE_APPS_LOCATION_SETTINGS";
	
	// User has agreed to use location for Google services.
	static final String USE_LOCATION_FOR_SERVICES_ON = "1";

	private static final String NAME = "name";
	private static final String VALUE = "value";

	private final ConnectionCallbacks connectionCallbacks = new ConnectionCallbacks() {
		public void onDisconnected() {}

		public void onConnected(Bundle bunlde) {
			handler.post(new Runnable() {
				public void run() {
					if (requestLastLocation != null && locationClient.isConnected()) {
						requestLastLocation.onLocationChanged(locationClient.getLastLocation());
						requestLastLocation = null;
					}
					if (requestLocationUpdates != null && locationClient.isConnected()) {
						LocationRequest locationRequest = new LocationRequest().setPriority(
								LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(requestLocationUpdatesTime)
								.setFastestInterval(requestLocationUpdatesTime)
								.setSmallestDisplacement(requestLocationUpdatesDistance);
						locationClient.requestLocationUpdates(
								locationRequest, requestLocationUpdates, handler.getLooper());
					}
				}
			});
		}
	};

	private final OnConnectionFailedListener
		onConnectionFailedListener = new OnConnectionFailedListener() {
			public void onConnectionFailed(ConnectionResult connectionResult) {}
	};

	private final Handler handler;
	private final LocationClient locationClient;
	private final LocationManager locationManager;
	private final ContentResolver contentResolver;
	private final GoogleSettingsObserver observer;

	private boolean isAvailable;
	private boolean isAllowed;
	private LocationListener requestLastLocation;
	private LocationListener requestLocationUpdates;
	private float requestLocationUpdatesDistance;
	private long requestLocationUpdatesTime;

	public GPSLocationManager(Context context, Looper looper, boolean enableLocaitonClient) {
		this.handler = new Handler(looper);

		if (enableLocaitonClient) {
			locationClient = new LocationClient(context, connectionCallbacks, onConnectionFailedListener);
			locationClient.connect();
		} else {
			locationClient = null;
		}

		locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		contentResolver = context.getContentResolver();
		observer = new GoogleSettingsObserver(handler);

		isAvailable = isAvailable(context);
		isAllowed = isUseLocationForServicesOn();

		contentResolver.registerContentObserver(
				Uri.parse(GOOGLE_SETTINGS_CONTENT_URI + "/" + USE_LOCATION_FOR_SERVICES), false, observer);
	}

	private static boolean isAvailable(Context context) {
	    Intent intent = new Intent(ACTION_GOOGLE_APPS_LOCATION_SETTINGS);
	    ResolveInfo resolveInfo = context.getPackageManager()
	        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
	    return resolveInfo != null;
	}
	
	/**
	 * Closes the {@link MyTracksLocationManager}.
	 */
	 public void close() {
		 if (locationClient != null) {
			 locationClient.disconnect();
		 }
		 contentResolver.unregisterContentObserver(observer);
	 }

	 /**
	  * Returns true if allowed to access the location manager. Returns true if
	  * there is no Google location settings or the Google location settings allows
	  * access to location data.
	  */
	 public boolean isAllowed() {
		 return isAllowed;
	 }

	 /**
	  * Returns true if gps provider is enabled.
	  */
	 public boolean isGpsProviderEnabled() {
		 if (!isAllowed) {
			 return false;
		 }
		 String provider = LocationManager.GPS_PROVIDER;
		 if (locationManager.getProvider(provider) == null) {
			 return false;
		 }
		 return locationManager.isProviderEnabled(provider);
	 }

	 /**
	  * Request last location.
	  * 
	  * @param locationListener location listener
	  */
	 public void requestLastLocation(final LocationListener locationListener) {
		 handler.post(new Runnable() {
			 public void run() {
				 if (!isAllowed) {
					 requestLastLocation = null;
					 locationListener.onLocationChanged(null);
				 } else {
					 requestLastLocation = locationListener;
					 connectionCallbacks.onConnected(null);
				 }
			 }
		 });
	 }

	 /**
	  * Requests location updates. This is an ongoing request, thus the caller
	  * needs to check the status of {@link #isAllowed}.
	  * 
	  * @param minTime the minimal time
	  * @param minDistance the minimal distance
	  * @param locationListener the location listener
	  */
	 public void requestLocationUpdates(
			 final long minTime, final float minDistance, final LocationListener locationListener) {
		 handler.post(new Runnable() {
			 public void run() {
				 requestLocationUpdatesTime = minTime;
				 requestLocationUpdatesDistance = minDistance;
				 requestLocationUpdates = locationListener;
				 connectionCallbacks.onConnected(null);
			 }
		 });
	 }

	 /**
	  * Removes location updates.
	  * 
	  * @param locationListener the location listener
	  */
	 public void removeLocationUpdates(final LocationListener locationListener) {
		 handler.post(new Runnable() {
			 public void run() {
				 requestLocationUpdates = null;
				 if (locationClient != null && locationClient.isConnected()) {
					 locationClient.removeLocationUpdates(locationListener);
				 }
			 }
		 });
	 }

	 /**
	  * Returns true if the Google location settings for
	  * {@link #USE_LOCATION_FOR_SERVICES} is on.
	  */
	 private boolean isUseLocationForServicesOn() {
		 if (!isAvailable) {
			 return true;
		 }
		 Cursor cursor = null;
		 try {
			 cursor = contentResolver.query(Uri.parse(GOOGLE_SETTINGS_CONTENT_URI), new String[] { VALUE },
					 NAME + "=?", new String[] { USE_LOCATION_FOR_SERVICES }, null);
			 if (cursor != null && cursor.moveToNext()) {
				 return USE_LOCATION_FOR_SERVICES_ON.equals(cursor.getString(0));
			 }
		 } catch (RuntimeException e) {
			 Log.w(TAG, "Failed to read " + USE_LOCATION_FOR_SERVICES, e);
		 } finally {
			 if (cursor != null) {
				 cursor.close();
			 }
		 }
		 return false;
	 }

}
