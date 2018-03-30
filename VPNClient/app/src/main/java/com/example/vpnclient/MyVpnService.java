package com.example.vpnclient;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MyVpnService extends VpnService implements Handler.Callback {
    private static final String TAG = MyVpnService.class.getSimpleName();
    //customer action for intent
    public static final String ACTION_CONNECT = "com.example.android.vpnclient.START";
    public static final String ACTION_DISCONNECT = "com.example.android.vpnclient.STOP";
    private Handler mHandler;
    private static class Connection extends Pair<Thread, ParcelFileDescriptor> {
        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }
    //Atomic objects for multithreading
    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();
    private AtomicInteger mNextConnectionId = new AtomicInteger(1);

    private PendingIntent mConfigureIntent;
    @Override
    public void onCreate() {
        //  handler is using only for show message
        if (mHandler == null) {
            mHandler = new Handler(this);

        }
        // Create the intent to "configure" the connection (just start VpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
   //onStartCommand is called every time a client starts the service using startService
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else {
            connect();
            return START_STICKY;
        }
    }
    @Override
    public void onDestroy() {
        disconnect();
    }
    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what);
        }
        return true;
    }
    private void connect() {

          // Become a foreground service.
        updateForegroundNotification(R.string.connecting);
        mHandler.sendEmptyMessage(R.string.connecting);
        // Extract information from the shared preferences.
        final SharedPreferences prefs = getSharedPreferences(MainActivity.Prefs.NAME, MODE_PRIVATE);
        final String server = prefs.getString(MainActivity.Prefs.SERVER_ADDRESS, "");
        final int port;
        try {//parse port
            port = Integer.parseInt(prefs.getString(MainActivity.Prefs.SERVER_PORT, ""));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Bad port: " + prefs.getString(MainActivity.Prefs.SERVER_PORT, null), e);
            return;
        }

        startConnection(new VpnConnection(
                this, mNextConnectionId.getAndIncrement(), server, port));
    }
    private void startConnection(final VpnConnection connection) {
        // replace old connection
        final Thread thread = new Thread(connection, "VpnThread");
        setConnectingThread(thread);
        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent);
        //implements method of interface
        connection.setOnEstablishListener(new VpnConnection.OnEstablishListener() {
            public void onEstablish(ParcelFileDescriptor tunInterface, String message) {
                if(message.equals("Error"))
                    mHandler.sendEmptyMessage(R.string.Errors);
                if(message.equals("Timeout"))
                    mHandler.sendEmptyMessage(R.string.Timeout);
                else{
                    mHandler.sendEmptyMessage(R.string.connected);
                    mConnectingThread.compareAndSet(thread, null);
                    setConnection(new Connection(thread, tunInterface));
                }

            }
        });
        thread.start();
    }
    //replace old connection new one
    private void setConnectingThread(final Thread thread) {
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if (oldThread != null) {
            oldThread.interrupt();
        }
    }

    private void setConnection(final Connection connection) {
        final Connection oldConnection = mConnection.getAndSet(connection);
        if (oldConnection != null) {
            try {
                oldConnection.first.interrupt();
                oldConnection.second.close();
            } catch (IOException e ) {
                Log.e(TAG, "Closing VPN interface", e);
            }
        }
    }
    private void disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected);
        setConnectingThread(null);
        setConnection(null);
        stopForeground(true);
    }
    private void updateForegroundNotification(final int message) {
        startForeground(1, new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build());
    }
}
