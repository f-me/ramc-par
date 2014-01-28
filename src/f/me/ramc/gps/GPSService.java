package f.me.ramc.gps;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.gms.location.LocationListener;

import f.me.ramc.MainActivity;
import f.me.ramc.R;
import f.me.ramc.util.SystemUtils;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class GPSService extends Service {

	public static final String BROADCAST_LOCATION_INTENT = "f.me.ramc.BROADCAST_LOCATION_INTENT";
	public static final String BROADCAST_LAST_LOCATION_UPDATE_INTENT = "f.me.ramc.BROADCAST_LAST_LOCATION_UPDATE_INTENT";
	public static final String LOCATION = "f.me.ramc.LOCATION";
	public static final String LOCATION_UPDATE_STATE = "f.me.ramc.LOCATION_UPDATE_STATE";
	
	private static final String TAG = GPSService.class.getSimpleName();
	private static final long ONE_SECOND = 1000; // in milliseconds
	private static final long ONE_MINUTE = 60 * ONE_SECOND; // in milliseconds
	
	private WakeLock wakeLock;
	private ExecutorService executorService;
	private GPSLocationManager gpsLocationManager;
	private Handler handler;
	private LocationSender locationSender;
	
	
	private ServiceBinder binder = new ServiceBinder(this);
	
	private LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(final Location location) {
			if (gpsLocationManager == null || executorService == null
					|| !gpsLocationManager.isAllowed() || executorService.isShutdown()
					|| executorService.isTerminated()) {
				return;
			}
			executorService.submit(new Runnable() {
				public void run() {
					onLocationChangedAsync(location);
				}
			});
		}
	};

	private final Runnable registerLocationRunnable = new Runnable() {
		public void run() {
			registerLocationListener();
			handler.postDelayed(this, ONE_MINUTE);
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		acquireWaleLock();
		locationSender = new LocationSender(this);
		executorService = Executors.newSingleThreadExecutor();
		handler = new Handler();
		gpsLocationManager = new GPSLocationManager(this, handler.getLooper(), true);
		handler.post(registerLocationRunnable);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleStartCommand(intent, startId);
		return START_STICKY;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		handleStartCommand(intent, startId);
	}
	
	private void handleStartCommand(Intent intent, int startId) {
		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		startForegroundService(pendingIntent, R.string.partner_id);
		Log.d(TAG, "GPSService started");
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onDestroy() {
		// Reverse order from onCreate    
		stopForegroundService();

		handler.removeCallbacks(registerLocationRunnable);
		unregisterLocationListener();

		gpsLocationManager.close();
		gpsLocationManager = null;        

		binder.detachFromService();
		binder = null;

		// This should be the next to last operation
		releaseWakeLock();

		/*
		 * Shutdown the executorService last to avoid sending events to a dead
		 * executor.
		 */
		executorService.shutdown();
		super.onDestroy();
	}

	/**
	 * Called when location changed.
	 * 
	 * @param location the location
	 */
	private void onLocationChangedAsync(Location location) {
		// Fix for phones that do not set the time field
		if (location.getTime() == 0L) {
			location.setTime(System.currentTimeMillis());
		}
		locationSender.send(location);
		broadcastLocation(location);
		Log.d(TAG, location.toString());
	}

	/**
	 * Registers the location listener.
	 */
	private void registerLocationListener() {
		if (gpsLocationManager == null) {
			Log.d(TAG, "locationManager is null.");
			return;
		}
		try {
			gpsLocationManager.requestLocationUpdates(
					LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS,
					LocationUtils.SMALLEST_DISPLACEMENT_IN_METERS,
					locationListener);
		} catch (RuntimeException e) {
			Log.d(TAG, "Could not register location listener.", e);
		}
	}

	/**
	 * Unregisters the location manager.
	 */
	private void unregisterLocationListener() {
		if (gpsLocationManager == null) {
			Log.d(TAG, "GPSLocationManager is null.");
			return;
		}
		gpsLocationManager.removeLocationUpdates(locationListener);
	}

	/**
	 * Starts the service as a foreground service.
	 * 
	 * @param pendingIntent the notification pending intent
	 * @param messageId the notification message id
	 */
	protected void startForegroundService(PendingIntent pendingIntent, int messageId) {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
				.setContentIntent(pendingIntent)
				.setContentText(getString(messageId))
				.setContentTitle(getString(R.string.app_name))
				.setOngoing(true)
				.setSmallIcon(R.drawable.ic_notify)
				.setWhen(System.currentTimeMillis());
		startForeground(1, builder.build());
	}

	/**
	 * Stops the service as a foreground service.
	 */
	protected void stopForegroundService() {
		stopForeground(true);
	}
	
	/**
	 * Starts gps.
	 */
	private void startGps() {
		acquireWaleLock();
		registerLocationListener();
	}

	/**
	 * Stops gps.
	 * 
	 * @param stop true to stop self
	 */
	private void stopGps(boolean stop) {
		unregisterLocationListener();
		stopForegroundService();
		releaseWakeLock();
		if (stop) {
			stopSelf();
		}
	}

	/**
	 *  Acquire the wake lock.
	 */
	private void acquireWaleLock() {
		wakeLock = SystemUtils.acquireWakeLock(this, wakeLock);
	}

	/**
	 * Releases the wake lock.
	 */
	private void releaseWakeLock() {
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
			wakeLock = null;
		}
	}

	private void broadcastLocation(Location location) {
		Intent intent = new Intent();
		intent.setAction(BROADCAST_LOCATION_INTENT);
		intent.putExtra(LOCATION, location);
		sendBroadcast(intent);
	}

	/**
	 * TODO: There is a bug in Android that leaks Binder instances. This bug is
	 * especially visible if we have a non-static class, as there is no way to
	 * nullify reference to the outer class (the service). A workaround is to use
	 * a static class and explicitly clear service and detach it from the
	 * underlying Binder. With this approach, we minimize the leak to 24 bytes per
	 * each service instance. For more details, see the following bug:
	 * http://code.google.com/p/android/issues/detail?id=6426.
	 */
	private static class ServiceBinder extends IGPSService.Stub {
		private GPSService gpsService;
		private DeathRecipient deathRecipient;

		public ServiceBinder(GPSService gpsService) {
			this.gpsService = gpsService;
		}

		public boolean isBinderAlive() {
			return gpsService != null;
		}

		public boolean pingBinder() {
			return isBinderAlive();
		}

		public void linkToDeath(DeathRecipient recipient, int flags) {
			deathRecipient = recipient;
		}

		public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
			if (!isBinderAlive()) {
				return false;
			}
			deathRecipient = null;
			return true;
		}

		public void startGps() {
			if (!canAccess()) {
				return;
			}
			
			gpsService.startGps();
		}

		public void stopGps() {
			if (!canAccess()) {
				return;
			}
			gpsService.stopGps(true);
		}

		public void setFree(boolean isFree) {
			gpsService.locationSender.setFree(isFree);
		}
		
		public String getLastSuccessfulRequest() {
			return gpsService.locationSender.getLastSuccessfulRequest();
		}
		
		/**
		 * Returns true if the RPC caller is from the same application or if the
		 * "Allow access" setting indicates that another app can invoke this
		 * service's RPCs.
		 */
		private boolean canAccess() {
			// As a precondition for access, must check if the service is available.
			if (gpsService == null) {
				throw new IllegalStateException("The GPS service has been detached!");
			}
			return Process.myPid() == Binder.getCallingPid();
		}

		/**
		 * Detaches from the track recording service. Clears the reference to the
		 * outer class to minimize the leak.
		 */
		private void detachFromService() {
			gpsService = null;

			if (deathRecipient != null) {
				deathRecipient.binderDied();
			}
		}
	}
}
