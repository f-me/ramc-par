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
import android.content.pm.PackageInfo;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.widget.Toast;

public class GpsService extends Service {
	/** Commands to the GpsService */
	
	// Toggle Busy/Free option for partner
	public static final String CMD_BUSY_FREE = "CMD_BUSY_FREE";
	
	final Messenger cmdHandler = new Messenger(new IncomingHandler());
	
	private LocationManager locMan;
    private LocationListener locListener;
    private boolean isFree = true;
    
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
	            ResponseHandler<String> responseHandler=new BasicResponseHandler();
	            return httpclient.execute(put, responseHandler);
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
			msgIntent.putExtra("isFree", isFree);
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
            return cmdHandler.getBinder();
    }
    
    public boolean setFree(boolean isFree) {
    	this.isFree = isFree;
    	return this.isFree;
    }
    
    /**
     * Handler of incoming commands.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	setFree(msg.getData().getBoolean(GpsService.CMD_BUSY_FREE, true));
        }
    }
}