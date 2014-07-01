package com.example.sample;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.example.sample.SensorTagData.SimpleKeysStatus;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;


public class ConnectionService extends IntentService implements BluetoothAdapter.LeScanCallback {
	
    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;
	private BluetoothGatt mConnectedGatt;
	
	private static final String TAG = "BluetoothGattActivity";
    private static final String DEVICE_NAME = "SensorTag";
    
    /* Accelerometer Sensor */
    private static final UUID ACCELEROMETER_SERVICE = UUID.fromString("f000aa10-0451-4000-b000-000000000000");
    private static final UUID ACCELEROMETER_DATA = UUID.fromString("f000aa11-0451-4000-b000-000000000000");
    private static final UUID ACCELEROMETER_CONF = UUID.fromString("f000aa12-0451-4000-b000-000000000000");
    private static final UUID ACCELEROMETER_PERIOD = UUID.fromString("f000aa13-0451-4000-b000-000000000000");
    
    /* Simple Keys */
    private static final UUID SIMPLE_KEYS_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID SIMPLE_KEYS_DATA = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    
    /* Client Configuration Descriptor */
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    // data storage
    //private ArrayList<String> sensorData;
    private int fileCounter;
    private SimpleKeysStatus state;
	protected Object log;
   
	
	public ConnectionService()
	{
		super("ConnectionService");
	}

	
	@Override
	public void onCreate()
	{
		super.onCreate();
		BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        mDevices = new SparseArray<BluetoothDevice>();
	}
	
	
	@Override
	protected void onHandleIntent(Intent intent)
	{
		mBluetoothAdapter.startLeScan(this);
	}

	@Override
	public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord)
	{
        /*
         * We are looking for SensorTag devices only, so validate the name
         * that each device reports before adding it to our collection
         */
        if (DEVICE_NAME.equals(device.getName()))
        {
            mDevices.put(device.hashCode(), device);
            mConnectedGatt = device.connectGatt(this, false, mGattCallback);
        }
	}

    
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
    	/* State Machine Tracking */
        private int mState = 0;
        private void reset() { mState = 0; }
        private void advance() { mState++; }
        
        private void setNextSensorCharacteristic(BluetoothGatt gatt)
        {
        	//SaveData(Thread.currentThread().toString() + " setNextSensorCharacteristic()\n");
        	BluetoothGattCharacteristic characteristic;
        	
        	switch (mState)
        	{
	        	case 0:
	        		// enable Accelerometer Sensor
	        		//SaveData(Thread.currentThread().toString() + " enabling Accelerometer\n");
	            	characteristic = gatt.getService(ACCELEROMETER_SERVICE)
	        			.getCharacteristic(ACCELEROMETER_CONF);
	            	characteristic.setValue(new byte[] { 0x01 });
	        		break;
	        	case 1:
	        		// set Accelerometer Sensor Period to 50ms
	        		//SaveData(Thread.currentThread().toString() + " setting Accelerometer period\n");
	            	characteristic = gatt.getService(ACCELEROMETER_SERVICE)
	        			.getCharacteristic(ACCELEROMETER_PERIOD);
	            	characteristic.setValue(new byte[] { (byte)5 });
	        		break;
            	default:
            		return;
        	}
        	//SaveData(Thread.currentThread().toString() + " writeCharacteristic()\n");
        	gatt.writeCharacteristic(characteristic);
        }
   
        
       /* * Enable notification of changes on the data characteristic for each sensor
        * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
        * configuration descriptor.
        */
       private void enableNextSensorNotification(BluetoothGatt gatt)
       {
    	   //SaveData(Thread.currentThread().toString() + " enableNextSensorNotification()\n");
    	   BluetoothGattCharacteristic characteristic;
    	   
    	   switch (mState) 
    	   {
	    	   case 1:
	    		   //SaveData(Thread.currentThread().toString() + " subscribing to Accelerometer data notifications\n");
	    		   characteristic = gatt.getService(ACCELEROMETER_SERVICE)
   		   				.getCharacteristic(ACCELEROMETER_DATA);
	    		   break;
	    	   case 0:
	    		   //SaveData(Thread.currentThread().toString() + " subscribing to Simple Keys data notifications\n");
	    		   characteristic = gatt.getService(SIMPLE_KEYS_SERVICE)
	    		   		.getCharacteristic(SIMPLE_KEYS_DATA);
	    		   break;
	    	   default:
	    		   return;
    	   }
    	   
    	   //Enable local notifications
    	   gatt.setCharacteristicNotification(characteristic, true);
			   
    	   //Enabled remote notifications
    	   BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
    	   desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    	   //SaveData(Thread.currentThread().toString() + " writeDescriptor()\n");
    	   gatt.writeDescriptor(desc);
       }
       
       
       @Override
       public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
       {
           if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED)
           {
               /*
                * Once successfully connected, we must next discover all the services on the
                * device before we can read and write their characteristics.
                */
               gatt.discoverServices();
           }
           else if (status != BluetoothGatt.GATT_SUCCESS)
           {
               /*
                * If there is a failure at any stage, simply disconnect
                */
               gatt.disconnect();
           }
       }
       

       @Override
       public void onServicesDiscovered(BluetoothGatt gatt, int status)
       {
    	   //SaveData(Thread.currentThread().toString() + " onServicesDiscovered()\n");
    	   reset();
    	   //sensorData = new ArrayList<String>();
    	   fileCounter = 0;
    	   state = SimpleKeysStatus.OFF_OFF;
    	   setNextSensorCharacteristic(gatt);
    	   
       }
       

       @Override
       public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
       {
       }
       

       @Override
       public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
       {
    	   //SaveData(Thread.currentThread().toString() + " onCharacteristicWrite()\n");
           //After writing to a sensor characteristic, we move on to enable data notification on the sensor
    	   enableNextSensorNotification(gatt);
       }
       
       
       @Override
       public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
       {
    	   //SaveData(Thread.currentThread().toString() + " onDescriptorWrite()\n");
    	   advance();
    	   setNextSensorCharacteristic(gatt);
       }
       

       @Override
       public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
       {
           /*
            * After notifications are enabled, all updates from the device on characteristic
            * value changes will be posted here.
            */
    	   if (ACCELEROMETER_DATA.equals(characteristic.getUuid())) //&& state.equals(SimpleKeysStatus.ON_OFF))
    	   {
    		   //SaveData("state= " + state.toString() + "\n");
    		   //if (state.equals(SimpleKeysStatus.ON_OFF))
    		   //{
    			   double[] acceleration = SensorTagData.extractAccelerometerXYZ(characteristic);
        		   Date d = new Date();
        		   SaveData(d.toString() + ": " + state.toString() + " " + String.valueOf(acceleration[0]) + ", " 
        				   + String.valueOf(acceleration[1]) + ", " + String.valueOf(acceleration[2]) + ";\n");
    		   //}
    		   Log.i("accelerometer",String.valueOf(acceleration[0]));
    	   }
    	   if (SIMPLE_KEYS_DATA.equals(characteristic.getUuid()))
    	   {
    		   state = SensorTagData.extractSimpleKeysValue(characteristic);
    		   //SaveData("SIMPLE KEYS NOTIFICATION, state= " + state.toString() + "\n");
    		   Date d = new Date();
    		   SaveData(d.toString() + ": " + state.toString() + "\n");
    		   Log.i("simple_click",state.toString());
//    		   if (state.equals(SimpleKeysStatus.OFF_OFF))
//    		   {
//    			   ++fileCounter;
//    		   }
    	   }
       }
   };
   
   
   private void SaveData(String string)
   {
        File sdCard = Environment.getExternalStorageDirectory();
        File directory = new File (sdCard.getAbsolutePath() + "/Data");
        if (!directory.exists())
        {
        	directory.mkdirs();
        }
        String fname = String.valueOf(fileCounter) + "sensor.txt";
        File file = new File (directory, fname);
        
        try
        {
	        if(!file.exists())
	        {
	            file.createNewFile();
	        }
	       FileOutputStream out = new FileOutputStream(file,true);
	       out.write(string.getBytes());
	       out.flush();
	       out.close();
        }
        catch (Exception e)
        {
               e.printStackTrace();
        }
    }
}




