package f.me.ramc.util;

import f.me.ramc.gps.LocationUtils;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class SystemUtils {

	private static final String TAG = SystemUtils.class.getSimpleName();

	private SystemUtils() {}

	/**
	 * Tries to acquire a partial wake lock if not already acquired. Logs errors
	 * and gives up trying in case the wake lock cannot be acquired.
	 */

	/**
	 * Acquire a wake lock if not already acquired.
	 * 
	 * @param context the context
	 * @param wakeLock wake lock or null
	 */
	@SuppressLint("Wakelock")
	public static WakeLock acquireWakeLock(Context context, WakeLock wakeLock) {
		Log.d(TAG, "Acquiring wake lock.");
		try {
			PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			if (powerManager == null) {
				Log.e(TAG, "Power manager null.");
				return wakeLock;
			}
			if (wakeLock == null) {
				wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LocationUtils.APPTAG);
				if (wakeLock == null) {
					Log.e(TAG, "Cannot create a new wake lock.");
				}
				return wakeLock;
			}
			if (!wakeLock.isHeld()) {
				wakeLock.acquire();
				if (!wakeLock.isHeld()) {
					Log.e(TAG, "Cannot acquire wake lock.");
				}
			}
		} catch (RuntimeException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return wakeLock;
	}
}
