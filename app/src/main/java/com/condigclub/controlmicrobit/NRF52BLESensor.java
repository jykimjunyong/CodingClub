package com.condigclub.controlmicrobit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Created by Jy.Kim on 10/31/2016.
 */
public class NRF52BLESensor {

    private static final String TAG = "BBC";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static final UUID NORDIC_UART_SERVICE = UUID
            .fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID NORDIC_UART_RX_CHARACTERISTIC = UUID
            .fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID NORDIC_UART_TX_CHARACTERISTIC = UUID
            .fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CONFIG_DESCRIPTOR = UUID
            .fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context mContext;
    private String mDeviceAddress;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothGatt mConnectedGatt;

    private Semaphore available;

    Handler bleHandler;

    private boolean isSeviceDiscovered = false;

    public NRF52BLESensor(Context context, String deviceAddress, String deviceName) {
        Log.d(TAG, "BLE Info:"+deviceAddress);

        mContext = context;
        mDeviceAddress = deviceAddress;

        BluetoothManager manager = (BluetoothManager) mContext
                .getSystemService(mContext.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        if (mDeviceAddress != null) {
            device = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
        }
        //bleHandler = new Handler();
    }

    protected void resume()
    {
        bleHandler = new Handler();
        if (mDeviceAddress != null) {
            mConnectedGatt = device.connectGatt(mContext, false, mGattCallback);
        }
    }

    protected void pause() {
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt.close();
            mConnectedGatt = null;
        }
    }


    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            Log.d(TAG, "Connection State Change: " + status + " -> "
                    + newState + ":" +connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS
                    && newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();

            } else if (status == BluetoothGatt.GATT_SUCCESS
                    && newState == BluetoothProfile.STATE_DISCONNECTED) {
                isSeviceDiscovered = false;
                announceConnection(false);

            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                isSeviceDiscovered = false;
                announceConnection(false);
                gatt.disconnect();
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: " + status);
            //TODO : When service discovered
            available = new Semaphore(1, true);
            isSeviceDiscovered = serviceCheck();
            if ( isSeviceDiscovered )
                announceConnection(true);
            else
                announceConnection(false);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            byte readData[] = characteristic.getValue();
            //displayPacketBin("onCharacteristicRead", characteristic, readData);
            if ( characteristic.getUuid().equals(NORDIC_UART_RX_CHARACTERISTIC) ) {


                unLockComm();
            }
            else if ( characteristic.getUuid().equals(NORDIC_UART_RX_CHARACTERISTIC) )
            {
                byte[] copiedData = Arrays.copyOf(readData, 20);

            }

            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            //Log.d(TAG, "onCharacteristicWrite: " + status);
            unLockComm();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            byte[] copiedData = characteristic.getValue();

            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorWrite:" + status);
            unLockComm();
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {

        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };

    public void enableNofitication(boolean enable)
    {
        final boolean fEnable = enable;
        if ( isSeviceDiscovered )
        {
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    BluetoothGattCharacteristic nrf52_data_notification;
                    try {
                        lockComm();
                        nrf52_data_notification = mConnectedGatt.getService(NORDIC_UART_SERVICE).getCharacteristic(NORDIC_UART_RX_CHARACTERISTIC);
                        mConnectedGatt.setCharacteristicNotification(nrf52_data_notification, fEnable);

                        BluetoothGattDescriptor desc = nrf52_data_notification.getDescriptor(CONFIG_DESCRIPTOR);
                        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mConnectedGatt.writeDescriptor(desc);

                    } catch ( NullPointerException e) {
                        unLockComm();
                        e.printStackTrace();
                        Log.e(TAG, "enableNofitication Error");
                    }
                }
            });

        }
        else
        {
            Toast.makeText(mContext, "BLE Service disconnected", Toast.LENGTH_LONG).show();
        }
    }


    private boolean serviceCheck()
    {

        boolean ret = false;
        BluetoothGattCharacteristic nrf52_config_rw;
        BluetoothGattCharacteristic test_char;
        try {
            nrf52_config_rw = mConnectedGatt.getService(NORDIC_UART_SERVICE).getCharacteristic(NORDIC_UART_RX_CHARACTERISTIC);
            ret = true;
            Log.d(TAG, "Service Found");
        } catch ( NullPointerException e) {
            e.printStackTrace();
            ret = false;
            Log.e(TAG, "Service Check Error");
        }
        return ret;
    }

    public void readRemoteRSSI()
    {
        if ( mConnectedGatt != null)
            mConnectedGatt.readRemoteRssi();
    }

    public void writeConfig(byte[] configData)
    {
        final byte[] cData = configData;

        if ( isSeviceDiscovered )
        {
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    BluetoothGattCharacteristic nrf52_config_rw;
                    try {
                        lockComm();
                        nrf52_config_rw = mConnectedGatt.getService(NORDIC_UART_SERVICE).getCharacteristic(NORDIC_UART_TX_CHARACTERISTIC);
                        nrf52_config_rw.setValue(cData);
                        mConnectedGatt.writeCharacteristic(nrf52_config_rw);


                    } catch ( NullPointerException e) {
                        unLockComm();
                        e.printStackTrace();
                        Log.e(TAG, "writeConfig Error");
                    }
                }
            });

        }
        else
        {
            //Toast.makeText(mContext, "BLE Service disconnected", Toast.LENGTH_LONG).show();
        }
    }

    private void announceConnection(boolean state)
    {
        Intent tIntent = new Intent("com.codingclub.microbit.BLE_CONN");
        tIntent.putExtra("STATE", state);
        try {
            // Log.d(TAG, "mContext:"+mContext);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(tIntent);
        } catch (NullPointerException e) {
            Log.d(TAG, "announceData error");
        }
    }


    private void lockComm()
    {
        try {
            //Log.d(TAG, "Before Acquire");
            available.acquire();
            //Log.d(TAG, "After Acquire");
        } catch (Exception e) {
        }
    }

    private void unLockComm()
    {
        available.release();
        //Log.d(TAG, "After Release");
    }

    private void displayPacketBin(String callback, BluetoothGattCharacteristic characteristics, byte[] packet)
    {
        int len = packet.length;

        Log.d(TAG, callback +":"+characteristics.getUuid());
        String message = "ReceivedPacket:";
        for ( int i = 0; i < len; i++)
        {
            message += String.format("%02x", packet[i])+",";
        }
        Log.d(TAG,message);
    }
}
