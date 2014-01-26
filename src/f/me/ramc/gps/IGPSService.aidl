package f.me.ramc.gps;

interface IGPSService {

	/**
	 * Starts gps.
	 */
	void startGps();

	/**
	 * Stops gps.
	 */
	void stopGps();

	/**
	 * Determines Partner status.
	 */
	void setFree(boolean isFree);
	
	String getLastSuccessfulRequest();
}