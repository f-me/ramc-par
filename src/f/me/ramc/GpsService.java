package f.me.ramc;

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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
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

    	new SendToRAMC().execute(loc);
	}

    private class SendToRAMC extends AsyncTask<Location, Void, String> {

		@Override
		protected String doInBackground(Location... locs) {
			try {
				// Create a KeyStore containing our trusted CAs
				KeyStore keystore = KeyStore.getInstance("PKCS12");
				keystore.load(getResources().openRawResource(R.raw.keystore), "".toCharArray());
	
				SSLSocketFactory sslSocketFactory = new AdditionalKeyStoresSSLSocketFactory(keystore);
	
				HttpParams params = new BasicHttpParams();
		        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
	
		        final SchemeRegistry registry = new SchemeRegistry();
		        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		        registry.register(new Scheme("https", sslSocketFactory, 40444));
	
				ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, registry);
				DefaultHttpClient http = new DefaultHttpClient(manager, params);
				
				String partnerId = getResources().getString(R.string.partner_id);
		    	String url = getResources().getString(R.string.ramc_url) + partnerId;
				HttpPut put = new HttpPut(url);

				Location loc = locs[0];
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
		        nameValuePairs.add(new BasicNameValuePair(
		        		"lon",String.format(Locale.US, "%.7f", loc.getLongitude())));
		        nameValuePairs.add(new BasicNameValuePair(
		        		"lat",String.format(Locale.US, "%.7f", loc.getLatitude())));
	        	put.setEntity(new UrlEncodedFormEntity(nameValuePairs));
	            ResponseHandler<String> responseHandler=new BasicResponseHandler();
	            return http.execute(put, responseHandler);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return "";
		}
    	
		@Override
		protected void onPostExecute(String msg) {
			if (msg != null && msg.length() != 0) {
	    		msgIntent.putExtra("message", msg);
	    	}
	    	sendBroadcast(msgIntent);
		}
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
        
    @Override
    public IBinder onBind(Intent intent) {
            return null;
    }
}