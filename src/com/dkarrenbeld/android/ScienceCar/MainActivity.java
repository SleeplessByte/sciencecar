package com.dkarrenbeld.android.ScienceCar;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class MainActivity extends Activity {
	// Debugging
    private static final String TAG = "ScienceCar Main";
    private static final boolean D = true;
	
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    
    // Name of the connected device
    private static String _connectedDeviceName = null;
    private static String _connectedToString = null;
    private static Context _applicationContext = null;
    private static ActionBar _actionBar = null;
    
    // Local Bluetooth adapter
    private BluetoothAdapter _bluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService _chatService = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Get local Bluetooth adapter
        _bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (_bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        _applicationContext = getApplicationContext();
        
    	int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB)
        	saveActionBar();
    }
    
    @TargetApi(11)
	private void saveActionBar()
    {
    	_actionBar = getActionBar();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.d(TAG, ">> Starting <<");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!_bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (_chatService == null) setupChatService();
        }
    }
    
    /**
     * Setups the chat communication service
     */
    private void setupChatService() {
        if (D) Log.d(TAG, "setupChat()");

         // Initialize the BluetoothChatService to perform bluetooth connections
        _chatService = new BluetoothChatService(this, _handler);
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.d(TAG, ">> Resuming <<");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (_chatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (_chatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              _chatService.start();
            }
        }
    }
    

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.d(TAG, ">> Pausing <<");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.d(TAG, ">> Stopping <<");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (_chatService != null) _chatService.stop();
        if(D) Log.d(TAG, ">> Destroying <<");
    }

    /**
     * Resulting activity process
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, true);
            }
            break;
        case REQUEST_CONNECT_DEVICE_INSECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, false);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChatService();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.e(TAG, "Bluetooth is not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * Connects to an device
     * @param data
     * @param secure
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);
        
        if (D) Log.i(TAG, "I would like to connect to " + device.getName() + " at " + device.getAddress());
        // Attempt to connect to the device
        _chatService.connect(device, secure);
        _connectedToString = getString(R.string.title_connected_to, device.getName());
        
        while(true)
        	sendMessage();
    }
    	
    /**
     *  The Handler that gets information back from the BluetoothChatService
     */
    private static final Handler _handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    setStatus(_connectedToString);
                    //mConversationArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                //mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                //mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                _connectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(_applicationContext, "Connected to "
                               + _connectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(_applicationContext, msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
    
    @TargetApi(11)
	private final static void setStatus(int resId) {
    	int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB)
        	_actionBar.setSubtitle(resId);
        
    }

    @TargetApi(11)
	private final static void setStatus(CharSequence subTitle) {
    	int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB)
        	_actionBar.setSubtitle(subTitle);
    }
    
    /**
     * Sends a message.
     */
    private void sendMessage() {
        // Check that we're actually connected before trying anything
        if (_chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the message bytes and tell the BluetoothChatService to write
        byte[] send = new byte[] { (byte) 143 }; //message.getBytes();
        _chatService.write(send);

        // Reset out string buffer to zero and clear the edit text field
        //mOutStringBuffer.setLength(0);
        
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            return true;
        }
        return false;
    }
}
