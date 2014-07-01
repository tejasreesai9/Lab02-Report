package com.example.sample;


import android.bluetooth.BluetoothGattCharacteristic;
import static java.lang.Math.pow;

/**
 * Helper functions taken or adapted from: 
 * http://processors.wiki.ti.com/index.php/SensorTag_User_Guide
 */
public class SensorTagData {
	
	// IR Temperature Sensor helper functions
	public static double extractAmbientTemperature(BluetoothGattCharacteristic c)
	{
	    int offset = 2;
	    return shortUnsignedAtOffset(c, offset) / 128.0;
	}

	public static double extractTargetTemperature(BluetoothGattCharacteristic c, double ambient)
	{
	    Integer twoByteValue = shortSignedAtOffset(c, 0);

	    double Vobj2 = twoByteValue.doubleValue();
	    Vobj2 *= 0.00000015625;

	    double Tdie = ambient + 273.15;

	    double S0 = 5.593E-14;	// Calibration factor
	    double a1 = 1.75E-3;
	    double a2 = -1.678E-5;
	    double b0 = -2.94E-5;
	    double b1 = -5.7E-7;
	    double b2 = 4.63E-9;
	    double c2 = 13.4;
	    double Tref = 298.15;
	    double S = S0*(1+a1*(Tdie - Tref)+a2*pow((Tdie - Tref),2));
	    double Vos = b0 + b1*(Tdie - Tref) + b2*pow((Tdie - Tref),2);
	    double fObj = (Vobj2 - Vos) + c2*pow((Vobj2 - Vos),2);
	    double tObj = pow(pow(Tdie,4) + (fObj/S),.25);

	    return tObj - 273.15;
	}
	
	// Humidity Sensor helper functions
	public static double extractRelativeHumidity(BluetoothGattCharacteristic c)
	{
	    int offset = shortUnsignedAtOffset(c, 2);
	    // bits [1..0] are status bits and need to be cleared according
	    // to the userguide, but the iOS code doesn't bother. It should
	    // have minimal impact.
	    offset = offset - (offset % 4);

	    return (-6f) + 125f * (offset / 65535f);
	}
	
	// Accelerometer Sensor helper functions
	public static double[] extractAccelerometerXYZ(BluetoothGattCharacteristic c)
	{
	    /*
	     * The accelerometer has the range [-2g, 2g] with unit (1/64)g.
	     *
	     * To convert from unit (1/64)g to unit g we divide by 64.
	     *
	     * (g = 9.81 m/s^2)
	     *
	     * The z value is multiplied with -1 to coincide
	     * with how we have arbitrarily defined the positive y direction.
	     * */

	    Integer x = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
	    Integer y = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 1);
	    Integer z = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 2) * -1;

	    double scaledX = x / 64.0;
	    double scaledY = y / 64.0;
	    double scaledZ = z / 64.0;

	    return new double[] { scaledX , scaledY, scaledZ };
	}
	
	// Gyroscope Sensor helper functions
	public static float[] extractGyroscopeXYZ(BluetoothGattCharacteristic c)
	{
	    float y = shortSignedAtOffset(c, 0) * (500f / 65536f) * -1;
	    float x = shortSignedAtOffset(c, 2) * (500f / 65536f);
	    float z = shortSignedAtOffset(c, 4) * (500f / 65536f);

	    return new float[] { x, y, z };
	}
	
	// Simple Keys helper functions
	public static SimpleKeysStatus extractSimpleKeysValue(BluetoothGattCharacteristic c) {
	    /*
	     * The key state is encoded into 1 unsigned byte.
	     * bit 0: right key.
	     * bit 1: left key.
	     * bit 2: side key (test mode only).
	     */
	    Integer encodedInteger = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

	    SimpleKeysStatus newValue = SimpleKeysStatus.values()[encodedInteger % 4];
	    return newValue;
	}

	static enum SimpleKeysStatus {
	    // Warning: The order in which these are defined matters.
	    OFF_OFF, OFF_ON, ON_OFF, ON_ON;
	}

	/**
	 * Gyroscope, Magnetometer, Barometer, IR temperature, Humidity
	 * all store 16 bit two's complement values in the awkward format
	 * LSB MSB, which cannot be directly parsed as getIntValue(FORMAT_SINT16, offset)
	 * because the bytes are stored in the "wrong" direction.
	 *
	 * This function extracts these 16 bit two's complement values.
	 * */
	private static Integer shortSignedAtOffset(BluetoothGattCharacteristic c, int offset)
	{
	    Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
	    Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, offset + 1); // Note: interpret MSB as signed.

	    return (upperByte << 8) + lowerByte;
	}

	private static Integer shortUnsignedAtOffset(BluetoothGattCharacteristic c, int offset)
	{
	    Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
	    Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1); // Note: interpret MSB as unsigned.

	    return (upperByte << 8) + lowerByte;
	}
}
