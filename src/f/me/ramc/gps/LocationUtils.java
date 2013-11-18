package f.me.ramc.gps;

public final class LocationUtils {

	/*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	
	public static final String APPTAG = "GPSService";
	
	public static final int UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
	public static final int FASTEST_INTERVAL_IN_MILLISECONDS = 5000;
}
