package com.example.deer.exappusbserial.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Deer on 6/9/2017 AD.
 */

public class UsbSerialService extends Service {

    public static final String ACTION_USB_READY = "com.example.deer.exappusbserial.USB_READY";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_NOT_SUPPORTED = "com.example.deer.exappusbserial.USB_NOT_SUPPORTED";
    public static final String ACTION_NO_USB = "com.example.deer.exappusbserial.NO_USB";
    public static final String ACTION_USB_PERMISSION_GRANTED = "com.example.deer.exappusbserial.USB_PERMISSION_GRANTED";
    public static final String ACTION_USB_PERMISSION_NOT_GRANTED= "com.example.deer.exappusbserial.USB_PERMISSION_NOT_GRANTED";
    public static final String ACTION_USB_DISCONNECTED = "com.example.deer.exappusbserial.USB_DISCONNECTED";
    public static final String ACTION_CDC_DRIVER_NOT_WORKING = "com.example.deer.exappusbserial.ACTION_CDC_DRIVER_NOT_WORKING";
    public static final String ACTION_USB_DEVICE_NOT_WORKING = "com.example.deer.exappusbserial.ACTION_USB_DEVICE_NOT_WORKING";

    public static final int MESSAGE_FROM_SERIAL_PORT = 0;
    public static final int CTS_CHANGE = 1;
    public static final int DSR_CHANGE = 2;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private static final int BAUD_RATE = 57600; // BaudRate. Change this value if you need

    public static boolean SERVICE_CONNECTED = false;

    private IBinder binder = new UsbBinder();

    private Context context;
    private Handler mHandler;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;

    private UsbSerialDriver serialDriver;
    private UsbSerialPort serialPort;

    private boolean serialPortConnected;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    // Data received from serial port will be received here.
    private final SerialInputOutputManager.Listener mListener = new SerialInputOutputManager.Listener() {
        @Override
        public void onNewData(byte[] data) {
            String message = HexDump.dumpHexString(data);
            if (mHandler != null)
                mHandler.obtainMessage(MESSAGE_FROM_SERIAL_PORT, message).sendToTarget();
        }

        @Override
        public void onRunError(Exception e) {
            Log.d("onRunError", "Runner stopped.");
        }
    };

    // Different notification from OS will be received here (USB attached, detached, permission responses...)
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) // User accepted our USB connection. Try to open the device as a serial port
                {
                    Intent mIntent = new Intent(ACTION_USB_PERMISSION_GRANTED);
                    context.sendBroadcast(mIntent);
                    connection = usbManager.openDevice(device);
                    new ConnectionThread().start();
                } else // User not accepted our USB connection. Send an Intent to the Main Activity
                {
                    Intent mIntent = new Intent(ACTION_USB_PERMISSION_NOT_GRANTED);
                    context.sendBroadcast(mIntent);
                }
            } else if (intent.getAction().equals(ACTION_USB_ATTACHED)) {
                if (!serialPortConnected)
                    //findSerialPortDevice(); // A USB device has been attached. Try to open it as a Serial port
                Log.d("TAG", "findSerialPortDevice");
            } else if (intent.getAction().equals(ACTION_USB_DETACHED)) {
                // USB device was disconnected. send an intent to the Main Activity
                Intent mIntent = new Intent(ACTION_USB_DISCONNECTED);
                context.sendBroadcast(mIntent);
                if (serialPortConnected) {
                    try {
                        serialPort.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                serialPortConnected = false;
            }
        }
    };

    /*
     * onCreate will be executed when service is started. It configures an IntentFilter to listen for
     * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        this.context = this;
        serialPortConnected = false;
        UsbSerialService.SERVICE_CONNECTED = true;
        setFilter();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        findSerialPortDevice();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UsbSerialService.SERVICE_CONNECTED = false;
    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // This function will be called from MainActivity to write data through Serial Port
    public void write(byte[] data, int timeoutMillis) throws IOException {
        if (serialPort != null)
            serialPort.write(data, timeoutMillis);
    }

    public void setHandler(Handler mHandler) {
        this.mHandler = mHandler;
    }

    // Find all available drivers from attached devices
    private void findSerialPortDevice() {
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (!availableDrivers.isEmpty()) {
            // Get Driver
            serialDriver = availableDrivers.get(0);
            // Get Device
            device = serialDriver.getDevice();
            if (device != null) {
                requestUserPermission();
            } else {
                connection = null;
                device = null;
            }
        } else {
            // There is no USB devices connected. Send an intent to MainActivity
            Intent intent = new Intent(ACTION_NO_USB);
            sendBroadcast(intent);
        }
    }

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);
        registerReceiver(usbReceiver, filter);
    }

    // Request user permission. The response will be received in the BroadcastReceiver
    private void requestUserPermission() {
        PendingIntent mPendingIntent = PendingIntent
                .getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPendingIntent);
    }

    public class UsbBinder extends Binder {
        public UsbSerialService getService() { return UsbSerialService.this; }
    }

    /*
     * A simple thread to open a serial port.
     * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
     */
    private class ConnectionThread extends Thread {
        @Override
        public void run() {
            super.run();
            serialPort = serialDriver.getPorts().get(0);
            if (serialPort != null) {
                try {
                    serialPort.open(connection);
                    serialPortConnected = true;
                    serialPort.setParameters(57600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                    mSerialIoManager = new SerialInputOutputManager(serialPort, mListener);
                    mExecutor.submit(mSerialIoManager);

                    // Everything went as expected. Send an intent to MainActivity
                    Intent intent = new Intent(ACTION_USB_READY);
                    context.sendBroadcast(intent);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                Intent intent = new Intent(ACTION_USB_NOT_SUPPORTED);
                context.sendBroadcast(intent);
            }
        }
    }
}














