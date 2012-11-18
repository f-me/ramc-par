package f.me.ramc;

import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;


public class MainActivity extends Activity {

	private Intent svcIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        svcIntent = new Intent(MainActivity.this, GpsService.class);
        startService(svcIntent);
    }
    
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	String locationInfo = intent.getStringExtra("locationInfo");
        	if (locationInfo != null) {
	        	TextView locationText = (TextView) findViewById(R.id.textLocation);
	        	locationText.setText(locationInfo);
        	}
        	String message = intent.getStringExtra("message");
        	if (message != null) {
        		TextView messageText = (TextView) findViewById(R.id.textMessage);
        		messageText.setText(message);
        	}
        }
    };

    @Override
    public void onResume() {
    	super.onResume();
		registerReceiver(broadcastReceiver, new IntentFilter(GpsService.BROADCAST_ACTION));
		
        LocationManager locMan = (LocationManager)getSystemService(LOCATION_SERVICE);        
        boolean isGpsEnabled = locMan
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
            
        if (!isGpsEnabled) {
        	showSettingsAlert();
        }        
    }

    
    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        alertDialog.setTitle("Настройки GPS");
        alertDialog.setMessage("Необходимо включить GPS. Перейти в меню с настройками?");
        alertDialog.setPositiveButton("Перейти", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
              Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
              MainActivity.this.startActivity(intent);
            }
        });

        alertDialog.setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            	dialog.cancel();
            }
        });

        alertDialog.show();
      }
}
