package f.me.ramc;

import org.apache.http.HttpResponse;

import f.me.ramc.gps.GPSService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.view.WindowManager;
import android.widget.TextView;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;

public class LogActivity extends Activity {

	private TextView mLastSuccess;
	private TextView mResponseLog;
	
	private boolean mIsBound = false;
	private Messenger mService = null;
	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	
	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  We are communicating with our
	        // service through an IDL interface, so get a client-side
	        // representation of that from the raw service object.
	        mService = new Messenger(service);

	        // We want to monitor the service for as long as we are
	        // connected to it.
	        try {
	        	Message msg = Message.obtain(null,
	                    GPSService.MSG_REGISTER_CLIENT);
	            msg.replyTo = mMessenger;
	            mService.send(msg);
	            
	            // Get last successful request.
	            msg = Message.obtain(null,
	                    GPSService.MSG_LAST_SUCCESS_REQUEST);
	            msg.replyTo = mMessenger;
	            mService.send(msg);
	        } catch (RemoteException e) {
	            // In this case the service has crashed before we could even
	            // do anything with it; we can count on soon being
	            // disconnected (and then reconnected if it can be restarted)
	            // so there is no need to do anything here.
	        }
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        mService = null;
	    }
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		mLastSuccess = (TextView) findViewById(R.id.lastSuccessTextView);
		mResponseLog = (TextView) findViewById(R.id.logTextView);
		mResponseLog.setMovementMethod(new ScrollingMovementMethod());
	}
	
	@Override
    public void onResume() {
    	super.onResume();
        doBindService();
    }
    
    @Override
    public void onPause() {
    	doUnbindService();
        super.onPause();
    }
	
    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        bindService(new Intent(this, 
                GPSService.class), mConnection, Context.BIND_IMPORTANT);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
        	// If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null,
                            GPSService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }
        	
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }
	
	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
	    @Override
	    public void handleMessage(Message msg) {
	    	String responseString = msg.getData().getString(GPSService.KEY_RESPONSE);
	        switch (msg.what) {
	            case GPSService.MSG_LAST_SUCCESS_REQUEST:
	            	mLastSuccess.setText(responseString);
	            	break;
	            case GPSService.MSG_LAST_RESPONSE:
	            	mResponseLog.setText(responseString + "\n\n" + mResponseLog.getText());
	                break;
	            default:
	                super.handleMessage(msg);
	        }
	    }
	}
}
