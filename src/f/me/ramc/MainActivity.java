package f.me.ramc;

import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;


public class MainActivity extends Activity {

	public static final String RAMC_PAR_PREFS = "RAMC_PAR_PREFS";
	
	private Intent svcIntent;
	private ServiceConnection svcConn;
	private boolean isBound = false;
	private Messenger msgSender;
	private ToggleButton isFreeBtn;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        svcIntent = new Intent(MainActivity.this, GpsService.class);
        svcConn = new ServiceConnection() {

			public void onServiceConnected(ComponentName name, IBinder service) {
				msgSender = new Messenger(service);
				isBound = true;
			}

			public void onServiceDisconnected(ComponentName name) {
				isBound = false;
			}
        	
        };
        
        isFreeBtn = (ToggleButton) findViewById(R.id.isFreeToggleButton);
        isFreeBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            	setFree(isChecked);
            }
        });
        
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
        	boolean isFree = intent.getBooleanExtra("isFree", true);
        	isFreeBtn.setChecked(isFree);
        }
    };

    @Override
    public void onResume() {
    	super.onResume();
		
    	// Bind to GpsService
        bindService(svcIntent, svcConn, Context.BIND_AUTO_CREATE);
    	
    	registerReceiver(broadcastReceiver, new IntentFilter(GpsService.BROADCAST_ACTION));
		
        LocationManager locMan = (LocationManager)getSystemService(LOCATION_SERVICE);        
        boolean isGpsEnabled = locMan
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
            
        if (!isGpsEnabled) {
        	showSettingsAlert();
        }
        
        // Restore preferences
        SharedPreferences settings = getSharedPreferences(RAMC_PAR_PREFS, 0);
        isFreeBtn.setChecked(settings.getBoolean("isFree", true));
    }
    
    @Override
    public void onPause() {
    	unregisterReceiver(broadcastReceiver);
    	
    	// Unbind from the GpsService
        if (isBound) {
            unbindService(svcConn);
            isBound = false;
        }
    	
    	// Save preferences
    	SharedPreferences settings = getSharedPreferences(RAMC_PAR_PREFS, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("isFree", isFreeBtn.isChecked());
        editor.commit();
        
        super.onPause();
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
    
    public void setFree(boolean isFree) {
    	Bundle cmd = new Bundle();
    	cmd.putBoolean(GpsService.CMD_BUSY_FREE, isFree);
    	
    	Message msg = new Message();
    	msg.setData(cmd);
    	
    	try {
			msgSender.send(msg);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_info:
            	Intent intent = new Intent(this, InfoActivity.class);
            	startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
}
