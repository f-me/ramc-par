package f.me.ramc.gps;

public final class LocationUtils {

	/*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	
	public static final String APPTAG = "GPSService";
	
	public static final int UPDATE_INTERVAL_IN_MILLISECONDS = 20000;
	public static final int SMALLEST_DISPLACEMENT_IN_METERS = 0;
	
	public static final int CONNECTION_TIMEOUT_IN_MILLISECONDS = 3000;
	public static final int SO_TIMEOUT_IN_MILLISECONDS = 10000;
	
}
