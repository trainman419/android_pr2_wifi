package ros.android.pr2wifi;

import org.ros.exception.RemoteException;
import org.ros.exception.ServiceNotFoundException;
import org.ros.node.Node;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseListener;
import org.ros.service.pr2_network_management.Wifi;
import org.ros.service.pr2_network_management.Wifi.Response;
import org.ros.service.pr2_network_management.WifiGet;

import ros.android.activity.RosAppActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class PR2WifiActivity extends RosAppActivity {
	
	// TODO: get service clients on demand so that they don't go stale
	// if the service disappears and reappears
	private ServiceClient<Wifi.Request, Wifi.Response> local_srv;
	private ServiceClient<Wifi.Request, Wifi.Response> client_srv;
	
	private String ssid; // the SSID that the app is connected to
	private boolean local_allow;
	private boolean client_allow;
	
	private Handler mHandler;
	public static final int TOAST_MSG = 1;
	public static final int LOCAL_MSG = 2;
	public static final int CLIENT_MSG = 3;
	
	private static final int DIALOG_PASS = 1; // invalid passphrase
	
	public void toast(String s) {
		mHandler.sendMessage(Message.obtain(mHandler, TOAST_MSG, s));
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	local_srv = null;
    	client_srv = null;
    	
    	// set up a temporary handler so that we have something to deal with
    	// errors that may happen on startup
    	mHandler = new Handler() {
    		public void handleMessage(Message m) {
    			if(m.what == TOAST_MSG) {
    				Toast.makeText(getApplicationContext(), m.obj.toString(), 
    						Toast.LENGTH_SHORT).show();
    				Log.d("PR2WifiActivity", "Made early toast: " + m.obj.toString());
    			}
    		}
    	};
    	setDefaultAppName("pr2_network_management/wifi.app");
    	setDashboardResource(R.id.top_bar);
    	setMainWindowResource(R.layout.main);
        super.onCreate(savedInstanceState);
        
        final Activity pr2wifi = this;
        
        final EditText local_ssid = (EditText)findViewById(R.id.local_ssid);
        final CheckBox local_enable = (CheckBox)findViewById(R.id.local_enable);
        final EditText local_pass = (EditText)findViewById(R.id.local_passphrase);
        final Spinner local_security = (Spinner)findViewById(R.id.local_security);
        final Button local_update = (Button)findViewById(R.id.local_update);
        
        final EditText client_ssid = (EditText)findViewById(R.id.client_ssid);
        final EditText client_pass = (EditText)findViewById(R.id.client_passphrase);
        final Spinner client_security = (Spinner)findViewById(R.id.client_security);
        final Button client_update = (Button)findViewById(R.id.client_update);
        
        // get the current WiFi access point SSID
        WifiManager w_manager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        WifiInfo w_info = w_manager.getConnectionInfo();
        ssid = w_info.getSSID();
        local_allow = false;
        client_allow = false;
        Log.i("PR2WifiActivity", "Connected to SSID: " + ssid);
        
        // message handler; so that UI updates happen on the UI thread
        mHandler = new Handler() {
        	public void handleMessage(Message m) {
        		WifiGet.Response r;
        		switch(m.what) {
        		case TOAST_MSG:
    				Toast.makeText(getApplicationContext(), m.obj.toString(), 
    						Toast.LENGTH_SHORT).show();
    				Log.d("PR2WifIActivity", "Made toast: " + m.obj.toString());
        			break;
        		case LOCAL_MSG:
        			r = (WifiGet.Response)m.obj;
        			local_ssid.setText(r.ssid);
        			local_enable.setChecked(r.enabled);
        			local_pass.setText(r.passphrase);
        			local_security.setSelection(r.security);
        			
        			// enable/disable controls
        			local_enable.setEnabled(local_allow);
    				local_update.setEnabled(local_allow);
        			if( local_allow ) {
        				local_ssid.setEnabled(r.enabled);
        				local_security.setEnabled(r.enabled);
        				local_pass.setEnabled( r.enabled &&  0 != r.security);
        			} else {
        				local_ssid.setEnabled(false);
        				local_security.setEnabled(false);
        				local_pass.setEnabled(false);        				
        			}
        			break;
        		case CLIENT_MSG:
        			r = (WifiGet.Response)m.obj;
        			client_ssid.setText(r.ssid);
        			client_pass.setText(r.passphrase);
        			client_security.setSelection(r.security);
        			
        			// enable/disable controls
        			client_update.setEnabled(client_allow);
    				client_ssid.setEnabled(client_allow);
    				client_security.setEnabled(client_allow);
    				client_pass.setEnabled(client_allow && 0 != r.security);
        			break;
        		}
        	}
        };
        
        // Local WIFI Setup
        local_enable.setOnCheckedChangeListener(new OnCheckedChangeListener(){
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				local_ssid.setEnabled(isChecked);
				local_security.setEnabled(isChecked);
				local_pass.setEnabled(isChecked && local_security.getSelectedItemPosition() != 0);
			}
        });
        
        local_update.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				// TODO: check that we aren't breaking our connection to the PR2
				if( ! local_allow ) return;
				boolean enabled = local_enable.isChecked();
				String ssid = local_ssid.getText().toString();
				String passphrase = local_pass.getText().toString();
				int security = local_security.getSelectedItemPosition();
				
				String message;
				if( enabled ) {
					message = "Enabling local wifi with SSID: " + ssid;
					if( security != 0) {
	            		// passphrase minimum 8; maximum 63 characters
						if( passphrase.length() < 8 || passphrase.length() > 63) {
							showDialog(DIALOG_PASS);
							return;
						}
	        			Resources r = pr2wifi.getResources();
	        			message += " with " + r.getStringArray(R.array.security_types)[security]
	        					+ " security";
					}
				} else {
					message = "Disabling local wifi";
				}
				
				toast(message);
				
				// make ROS service call
				if( null != local_srv ) {
					Wifi.Request req = new Wifi.Request();
					req.enabled = enabled;
					req.ssid = ssid;
					req.security = (byte)security;
					req.passphrase = passphrase;
					local_srv.call(req, 
							new ServiceResponseListener<Wifi.Response>(){
								public void onFailure(RemoteException arg0) {
									String message = "Local wifi update failed";
									Log.e("PR2WifiActivity", message);
									toast(message);
								}
								public void onSuccess(Response arg0) {
									String message = "Local wifi update succeeded";
									Log.i("PR2WifiActivity", message);
									toast(message);
								}
							}
					);
				}
			}
        });
        
        local_security.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int position, long id) {
				local_pass.setEnabled( 0 != position );
			}
			public void onNothingSelected(AdapterView<?> arg0) {
				// Do nothing, until we decide we need to do otherwise
			}
        });
        
        // Client WIFI Setup
        client_security.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int position, long id) {
				client_pass.setEnabled(position != 0);
			}
			public void onNothingSelected(AdapterView<?> arg0) {
				// Do nothing, until we decide we need to do otherwise
			}
        });
        
        client_update.setOnClickListener(new OnClickListener(){
        	public void onClick(View v) {
        		// TODO: check that we aren't breaking our connection to the PR2
        		if( ! client_allow ) return;
        		String ssid = client_ssid.getText().toString();
        		String passphrase = client_pass.getText().toString();
        		int security = client_security.getSelectedItemPosition();
        		
        		String message = "Associating PR2 Wifi client with " + ssid;
        		if( security != 0 ) {
            		// passphrase minimum 8; maximum 63 characters
        			if( passphrase.length() < 8 || passphrase.length() > 63 ) {
        				showDialog(DIALOG_PASS);
        				return;
        			}
        			Resources r = pr2wifi.getResources();
        			message += " with " + r.getStringArray(R.array.security_types)[security]
        					+ " security";
        		}
        		toast(message);
        		
        		// make ROS service call
        		if( null != client_srv ) {
        			Wifi.Request req = new Wifi.Request();
        			req.enabled = true;
        			req.ssid = ssid;
        			req.security = (byte)security; //security;
        			req.passphrase = passphrase;
        			
        			client_srv.call(req, new ServiceResponseListener<Wifi.Response>() {
						public void onFailure(RemoteException arg0) {
							String message = "Client wifi update failed";
							Log.e("PR2WifiActivity", message);
							toast(message);
						}
						public void onSuccess(Response arg0) {
							String message = "Client wifi update succeeded";
							Log.i("PR2WifiActivity", message);
							toast(message);
						}
        			});
        		}
        	}
        });
    }
    
    protected Dialog onCreateDialog(int id) {
    	Dialog d = null;
    	switch(id) {
    	case DIALOG_PASS:
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    		builder.setMessage("Invalid passphrase; passphrase must be between"
    				+ " 8 and 63 characters long");
    		builder.setCancelable(false);
    		builder.setPositiveButton("Ok", null);
    		d = builder.create();
    		break;
    	}
    	return d;
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    }

    @Override
    public void onResume() {
    	super.onResume();
    }
    
    // ROS callbacks
    @Override
    protected void onNodeCreate(Node node) {
    	super.onNodeCreate(node);
    	try {
    		local_srv = node.newServiceClient("pr2_wifi/local", 
    			"pr2_network_management/Wifi");
    		client_srv = node.newServiceClient("pr2_wifi/client", 
    			"pr2_network_management/Wifi");
    		
    		ServiceClient<WifiGet.Request, WifiGet.Response> get;
    		WifiGet.Request req;
    		get = node.newServiceClient("pr2_wifi/local_get",
    				"pr2_network_management/WifiGet");
    		req = new WifiGet.Request();
    		get.call(req, new ServiceResponseListener<WifiGet.Response>() {
				public void onFailure(RemoteException arg0) {
					Log.e("PR2WifiActivity", "Failed to get local state");
				}
				public void onSuccess(WifiGet.Response arg0) {
					Log.i("PR2WifiActivity", "Got local state. SSID: " + arg0.ssid);
					if( arg0.ssid.equals(ssid) ) {
						Log.i("PR2WifiActivity", "Connected to local wifi");
						local_allow = false;
						client_allow = true;
					} else {
						Log.i("PR2WifiActivity", "Not connected to local wifi");
						local_allow = true;
						client_allow = false;
					}
					mHandler.sendMessage(mHandler.obtainMessage(LOCAL_MSG, arg0));
				}
    		});

    		get = node.newServiceClient("pr2_wifi/client_get",
    				"pr2_network_management/WifiGet");
    		req = new WifiGet.Request();
    		get.call(req, new ServiceResponseListener<WifiGet.Response>() {
				public void onFailure(RemoteException arg0) {
					Log.e("PR2WifiActivity", "Failed to get client state");
				}
				public void onSuccess(WifiGet.Response arg0) {
					Log.i("PR2WifiActivity", "Got client state. SSID: " + arg0.ssid);
					mHandler.sendMessage(mHandler.obtainMessage(CLIENT_MSG, arg0));
				}
    		});
    	
    	} catch( ServiceNotFoundException e ) {
    		String message = "Failed to create serice clients: " + e.toString();
    		Log.e("PR2WifiActivity", message);
    		toast(message);
    	}
    }
    
    @Override
    protected void onNodeDestroy(Node node) {
    	super.onNodeDestroy(node);
    	local_srv = null;
    	client_srv = null;
    }
}