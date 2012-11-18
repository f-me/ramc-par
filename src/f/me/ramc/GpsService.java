package f.me.ramc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

public class GpsService extends Service {
	private LocationManager locMan;
    private LocationListener locListener;
    
    private static long  minTimeMillis = 2000;
    public static final String BROADCAST_ACTION = "f.me.ramc.GpsService.message";
    private Intent msgIntent;

    
    private void startLoggerService() {
    	Criteria criteria = new Criteria();
	    criteria.setAccuracy(Criteria.ACCURACY_LOW);
	    criteria.setPowerRequirement(Criteria.POWER_LOW);
	    criteria.setAltitudeRequired(false);
	    criteria.setBearingRequired(false);
	    
        locMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String provider = locMan.getBestProvider(criteria, true);
        Location loc = locMan.getLastKnownLocation(provider);
        if (loc != null) {
        	sendLocation(loc);
        }
        
        locListener = new MyLocationListener();        
        locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMillis, 0, locListener);
        locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMillis, 0, locListener);
    }

    
    private void shutdownLoggerService() {
        locMan.removeUpdates(locListener);
    }

    
    private void sendLocation(Location loc) {    	
		String locationInfo = "Текущие координаты: "
	    		+ String.format(Locale.US, "%.5f", loc.getLongitude()) + "; "
	    		+ String.format(Locale.US, "%.5f", loc.getLatitude()) + "\n";
		
		if (loc.hasAccuracy()) {
			locationInfo += "Погрешность: " + (int) loc.getAccuracy() + " м";
		}
	
		msgIntent.putExtra("locationInfo", locationInfo);		

    	String partnerId = getResources().getString(R.string.partner_id);
    	String msg = putLocation(
    			loc,
    			getResources().getString(R.string.ramc_url) + partnerId);
    	
    	if (msg == null || msg.length() == 0) {
        	msg = putLocation(
        		loc,
        		getResources().getString(R.string.test_url) + partnerId);
    	}
    	
    	if (msg != null && msg.length() != 0) {
    		msgIntent.putExtra("message", msg);
    	}
    	sendBroadcast(msgIntent);
	}
    
    private String putLocation(Location loc, String url) {
    	List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair(
        		"lon",String.format(Locale.US, "%.7f", loc.getLongitude())));
        nameValuePairs.add(new BasicNameValuePair(
        		"lat",String.format(Locale.US, "%.7f", loc.getLatitude())));
        try {
        	HttpClient http = new DefaultHttpClient();
            HttpPut put = new HttpPut(url);

        	put.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            ResponseHandler<String> responseHandler=new BasicResponseHandler();
            return http.execute(put, responseHandler);
        } catch (Throwable e) {
        	e.printStackTrace();
        }
        return "";
    }

    
    public class MyLocationListener implements LocationListener {
        
        public void onLocationChanged(Location loc) {
            if (loc != null) {
            	sendLocation(loc);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            String showStatus = "undefined";
            if (status == LocationProvider.AVAILABLE)
                    showStatus = "Available";
            if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
                    showStatus = "Temporarily Unavailable";
            if (status == LocationProvider.OUT_OF_SERVICE)
                    showStatus = "Out of Service";
        	Toast.makeText(
                    getBaseContext(),
                    showStatus,
                    Toast.LENGTH_SHORT).show();
        }

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}
    }


    @Override
    public void onCreate() {
        super.onCreate();
        msgIntent = new Intent(BROADCAST_ACTION);
        startLoggerService();
    	Toast.makeText(
                getBaseContext(),
                "Запущена служба RAMC GPS",
                Toast.LENGTH_SHORT).show();
    }
    

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownLoggerService();
        Toast.makeText(
                getBaseContext(),
                "Остановлена служба RAMC GPS",
                Toast.LENGTH_SHORT).show();
    }


    private void showNotification(String text) {
    	NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Notification notification = new Notification(R.drawable.ic_notify,
                        text, System.currentTimeMillis());
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), 0);
        notification.setLatestEventInfo(this, "RAMC",
                        text, contentIntent);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        mNM.notify(0, notification);
    }

        
    @Override
    public IBinder onBind(Intent intent) {
            return null;
    }
}