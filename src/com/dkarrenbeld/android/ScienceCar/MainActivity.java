package com.dkarrenbeld.android.ScienceCar;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements SensorEventListener {
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
    
    // Sensor
	private static final double NOISE = 0.5;
    
    // Name of the connected device
    private static String _connectedDeviceName = null;
    
    // Static members for static functions
    private static String _connectedToString = null;
    private static Context _applicationContext = null;
    private static ActionBar _actionBar = null;
    
    // Local Bluetooth adapter
    private BluetoothAdapter _bluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService _chatService = null;
    
    private Timer _timer = null;
    
	protected byte _currentSpeed;
	protected byte _currentDirection = (byte) 0x10;
	
	protected ToggleButton _toggleSensor;
	protected Button _buttonAdd, _buttonClear, _buttonGo;
	protected EditText _editDelay, _editCode;
	protected SensorManager _sensorManager;
	protected Sensor _accelerometer;
	
	protected TextView _sensorXtxt, _sensorYtxt, _sensorZtxt;
	protected TextView _speedtxt;
	protected TextView _directiontxt;
	protected TextView _commandtxt;
	
	private int _speed0, _speed1, _speed2, _speed3, _speed4, _speed5,
		_speed6, _speed7, _speed8, _speed9, _speedA, _speedB, _speedC,
		_speedD, _speedE, _speedF, _dirN, _dirS, _dirE, _dirW;
	
	private int[] _speedTxtId, _speedCommandId;
	
	protected double _previousSpeed;
	protected double _previousDirection;
	protected List<Command> _programmedCommands;
	protected ArrayAdapter<String> _commandAdapter;
	
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
        
        // We can save this static. Android applications usually
        // only have one instance at the same time running.
        _applicationContext = getApplicationContext();
        
    	if (HoneycombOrHigher())
        	saveActionBar();
    	
    	// Do the sensor
    	_sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    	_accelerometer = _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

    	_sensorManager.registerListener(this, _accelerometer, SensorManager.SENSOR_DELAY_GAME);
    	
    	// Get views
    	_sensorXtxt =(TextView)findViewById(R.id.sensorX); // create X axis object
		_sensorYtxt =(TextView)findViewById(R.id.sensorY); // create Y axis object
		_sensorZtxt =(TextView)findViewById(R.id.sensorZ); // create Z axis object
		
		_speedtxt =(TextView)findViewById(R.id.speedTxt); // create speed object
		_directiontxt =(TextView)findViewById(R.id.directionTxt); // create direction object
		_commandtxt =(TextView)findViewById(R.id.commandTxt); // create command object
		
		// Get button
		_toggleSensor = (ToggleButton)findViewById(R.id.toggleSensor);
		_editDelay = (EditText)findViewById(R.id.editDelay);
		_editCode = (EditText)findViewById(R.id.editCode);
		_buttonAdd = (Button)findViewById(R.id.buttonAdd);
		_buttonClear = (Button)findViewById(R.id.buttonClear);
		_buttonGo = (Button)findViewById(R.id.buttonGo);
		
		_buttonAdd.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (_editCode.getText().length() != 2)
					return;
				if (_editDelay.getText().length() == 0)
					_editDelay.setText("0");
				Command command = new Command(_editCode.getText().toString(), (long)(Math.ceil(Double.parseDouble(_editDelay.getText().toString()))));
				if (!command.isValid())
					return;
				_commandAdapter.add("0x" + _editCode.getText().toString() + " for " + _editDelay.getText().toString() + " seconds" );
				_editCode.getText().clear();
				_editDelay.getText().clear();
			}
		});
		_buttonClear.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				_commandAdapter.clear();
			}
			
		});
		_buttonGo.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				_timer.cancel();
				_timer.purge();
				_timer = new Timer();
				
				if (_chatService != null && _chatService.getState() == BluetoothChatService.STATE_CONNECTED)
					sendMessage((byte)0);
				
				long ndelay = 3 * 1000;
				long tdelay = 0;
				for (int i = 0; i < _commandAdapter.getCount(); i++) {
					String item = _commandAdapter.getItem(i);
					String[] parts = item.split(" "); 
					String code = parts[0]; 
					String delay = parts[2];
					
					Command c = new Command(code, (long)Math.ceil(Double.parseDouble(delay) * 1000));
					
					if (!c.isValid())
						continue;
					tdelay += ndelay;
					_timer.schedule(c.ScheduledTask(), tdelay);
					ndelay = c.Delay();
					Log.i(TAG, "for " + ndelay + " ms");
				}
				
				tdelay += ndelay;
				Command e = new Command("0x00", 0);
				_timer.schedule(e.ScheduledTask(), tdelay);
				
			}
		});
		
		// Get id's
		_speed0 = R.string.speed_0;
		_speed1 = R.string.speed_1;
		_speed2 = R.string.speed_2;
		_speed3 = R.string.speed_3;
		_speed4 = R.string.speed_4;
		_speed5 = R.string.speed_5;
		_speed6 = R.string.speed_6;
		_speed7 = R.string.speed_7;
		_speed8 = R.string.speed_8;
		_speed9 = R.string.speed_9;
		_speedA = R.string.speed_A;
		_speedB = R.string.speed_B;
		_speedC = R.string.speed_C;
		_speedD = R.string.speed_D;
		_speedE = R.string.speed_E;
		_speedF = R.string.speed_F;
		
		_speedTxtId = new int[] { _speed0, _speed1, _speed2, _speed3, _speed4, _speed5,
				_speed6, _speed7, _speed8, _speed9, _speedA, _speedB, _speedC,
				_speedD, _speedE, _speedF };
		
		_speedCommandId = new int[] { DaguCarCommands.STOP, DaguCarCommands.SLOWEST, DaguCarCommands.WAY_WAY_SLOW,
				DaguCarCommands.WAY_SLOW, DaguCarCommands.LESS_WAY_SLOW, DaguCarCommands.SLOW, DaguCarCommands.EASY_GOING, 
				DaguCarCommands.CRUISING, DaguCarCommands.MOVING_RIGHT_ALONG, DaguCarCommands.MOVING_QUICK, 
				DaguCarCommands.MOVING_QUICKER, DaguCarCommands.MOVING_PRETTY_DARN_QUICK, DaguCarCommands.FAST, 
				DaguCarCommands.FASTER, DaguCarCommands.FASTEST, DaguCarCommands.I_LIED_THIS_IS_FASTEST
		};
		
		_dirN = R.string.dir_N;
		_dirS = R.string.dir_S;
		_dirW = R.string.dir_W;
		_dirE = R.string.dir_E;
		
		_commandtxt.setText(getString(R.string.commandTxt, String.format("%#x", 0x00)));
		_speedtxt.setText(getString(R.string.speedTxt, getString(speedTxtIdForSpeed())));
		_directiontxt.setText(getString(R.string.directionTxt, getDirectionTxt()));
		
		// Timer
		_timer = new Timer();
		
		// Adapter
		_commandAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
		_commandAdapter.add("0x18 for 0.75 seconds");
		_commandAdapter.add("0x77 for 0.25 seconds");
		_commandAdapter.add("0x89 for 0.50 seconds");
		_commandAdapter.add("0x00 for 2 seconds");
		_commandAdapter.add("0x6B for 1 seconds");
		
		ListView commandList = (ListView) findViewById(R.id.commandList);
		commandList.setAdapter(_commandAdapter);

    }
    
    ArrayList<String> list;
    
    
    @TargetApi(11)
	private void saveActionBar()
    {
    	if (HoneycombOrHigher())
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
        
        _sensorManager.registerListener(this, _accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }
    

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.d(TAG, ">> Pausing <<");
        _sensorManager.unregisterListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.d(TAG, ">> Stopping <<");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(D) Log.d(TAG, ">> Destroying <<");
        
        // Stop the Bluetooth chat services
        if (_chatService != null) _chatService.stop();
        _sensorManager.unregisterListener(this);
        _timer.cancel();
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
    
    static boolean HoneycombOrHigher() {
    	return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB;
    }
    
    /**
     * Sets the status bar subtitle
     * @param resId
     */
    @TargetApi(11)
	private final static void setStatus(int resId) {
    	if (HoneycombOrHigher())
        	_actionBar.setSubtitle(resId);
        
    }

    /**
     * Sets the status bar subtitle
     * @param subTitle
     */
    @TargetApi(11)
	private final static void setStatus(CharSequence subTitle) {
    	if (HoneycombOrHigher())
        	_actionBar.setSubtitle(subTitle);
    }
    
    /**
     * Sends a message.
     */
    private void sendMessage(byte controlbyte) {
        // Check that we're actually connected before trying anything
        if (_chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the message bytes and tell the BluetoothChatService to write
        byte[] send = new byte[] { (byte) controlbyte }; //message.getBytes();
        _chatService.write(send);
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

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		_sensorXtxt.setText(getString(R.string.sensorX, event.values[0]));
		_sensorYtxt.setText(getString(R.string.sensorY, event.values[1]));
		_sensorZtxt.setText(getString(R.string.sensorZ, event.values[2]));
		
		boolean hasChanged = false;
		boolean hasDirChanged = false;
		double newSpeedValue = (Math.max(-7.4, Math.min(7.4, event.values[0]))) / 7f * 0x0F;
		if (Math.abs(newSpeedValue - _previousSpeed) > NOISE) {
			_currentSpeed = (byte) (Math.floor(Math.abs(newSpeedValue)));
			hasDirChanged |= _previousSpeed < 0 && newSpeedValue > 0;
			hasDirChanged |= newSpeedValue < 0 && _previousSpeed > 0;
			_previousSpeed = newSpeedValue;
			_speedtxt.setText(getString(R.string.speedTxt, getString(speedTxtIdForSpeed())));
			hasChanged |= true;
		}
		
		double newDirectionValue = event.values[1];
		if (Math.abs(newDirectionValue - _previousDirection) > NOISE || hasDirChanged)
		{		
			if (newSpeedValue >= 0)
				if (Math.abs(newDirectionValue) < 1)
					_currentDirection = DaguCarCommands.NORTH;
				else if (newDirectionValue < 0)
					_currentDirection = DaguCarCommands.NORTHEAST;
				else
					_currentDirection = DaguCarCommands.NORTHWEST;
			else if (newSpeedValue <= 0)
				if (Math.abs(newDirectionValue) < 1)
					_currentDirection = DaguCarCommands.SOUTH;
				else if (newDirectionValue < 0)
					_currentDirection = DaguCarCommands.SOUTHEAST;
				else
					_currentDirection = DaguCarCommands.SOUTHWEST;
			
			_previousDirection = newDirectionValue;
			_directiontxt.setText(getString(R.string.directionTxt, getDirectionTxt()));
			hasChanged |= true;
		}
				
		if (hasChanged && _toggleSensor.isChecked()) {
			if (D) Log.i(TAG, "Sensor changed command with speed: " + _currentSpeed + "/dir: " + _currentDirection);
			byte commandByte = DaguCarCommands.CreateCommand(_currentDirection, _currentSpeed);
			_commandtxt.setText(getString(R.string.commandTxt, String.format("%#x", commandByte)));
			if (_chatService != null && _chatService.getState() == BluetoothChatService.STATE_CONNECTED)
				sendMessage(commandByte);
		} else if (hasChanged)
			_commandtxt.setText(getString(R.string.commandTxt, getString(R.string.commandDisabled)));
	}
	
	/**
	 * 
	 * @return
	 */
	private int speedTxtIdForSpeed() {
		for (int i = 0; i < _speedCommandId.length; i++)
		{
			if (_speedCommandId[i] == _currentSpeed)
				return _speedTxtId[i];
		}
		
		return _speedTxtId[0];
	}
	
	/**
	 * Gets the direction text for the current direction
	 * @return
	 */
	private CharSequence getDirectionTxt() {
		switch(_currentDirection) {
			case DaguCarCommands.NORTH:
				return getString(_dirN);
			case DaguCarCommands.SOUTH:
				return getString(_dirS);
			case DaguCarCommands.WEST:
				return getString(_dirW);
			case DaguCarCommands.EAST:
				return getString(_dirE);
			case DaguCarCommands.NORTHEAST:
				return getString(_dirN) + " " + getString(_dirE);
			case DaguCarCommands.SOUTHEAST:
				return getString(_dirS) + " " + getString(_dirE);
			case DaguCarCommands.NORTHWEST:
				return getString(_dirN) + " " + getString(_dirW);
			case DaguCarCommands.SOUTHWEST:
				return getString(_dirS) + " " + getString(_dirW);
			default:
				return "None";
		}
	}
	
	class SendControlByteTask extends TimerTask {
		private byte _controlByte;
		public SendControlByteTask(byte arg)
		{
			_controlByte = arg;
		}
		
        public void run() {
        	if (D) Log.i(TAG, "Sending " + _controlByte);
        	if (_chatService != null && _chatService.getState() == BluetoothChatService.STATE_CONNECTED)
        		sendMessage(_controlByte);
        }
    }
	
	class Command {
		private String _control;
		private long _delay;
		public Command(String control, long delay)
		{
			_control = control.replace("0x", "");
			_delay = delay;
		}
		
		/**
		 * 
		 * @return
		 */
		public boolean isValid()
		{
			try {
				int a = Integer.parseInt(_control, 16);
				if (a >= 0 && a <= 255)
					return true;
				Log.e(TAG, "out of range: " + a);
			} catch (NumberFormatException e) {
				Log.e(TAG, e.getMessage());
			}

			return false;
		}
		
		public TimerTask ScheduledTask()
		{
			return new SendControlByteTask((byte)Integer.parseInt(_control, 16));
		}
		
		public long Delay()
		{
			return _delay;
		}
	}
}
