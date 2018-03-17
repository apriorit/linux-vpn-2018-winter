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
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AppCompatActivity;

import com.example.vpnclient.R;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MyVpnService extends VpnService implements Handler.Callback {
    private static final String TAG = MyVpnService.class.getSimpleName();
    public static final String ACTION_CONNECT = "com.example.android.vpnclient.START";
    public static final String ACTION_DISCONNECT = "com.example.android.vpnclient.STOP";
    private Handler mHandler;
    private static class Connection extends Pair<Thread, ParcelFileDescriptor> {
        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }
    //Для многопоточности
    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();
    private AtomicInteger mNextConnectionId = new AtomicInteger(1);
    //Для передачи сообщений в  MainActivity
    private PendingIntent mConfigureIntent;
    @Override
    public void onCreate() {
        //  handler используется только для отображения сообщения
        if (mHandler == null) {
            mHandler = new Handler(this);

        }
        // Создать intent для "конфигурации" соединения (просто запуск VpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
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

        /*Перейти на передний план. Фоновые сервисы также могут быть  VPN, но они могут
        быть отключены фоновой проверкой, прежде чем получат шанс получить onRevoke ().*/
        updateForegroundNotification(R.string.connecting);
        mHandler.sendEmptyMessage(R.string.connecting);
        // Extract information from the shared preferences.
        final SharedPreferences prefs = getSharedPreferences(MainActivity.Prefs.NAME, MODE_PRIVATE);
        final String server = prefs.getString(MainActivity.Prefs.SERVER_ADDRESS, "");
        final int port;

        try {
            port = Integer.parseInt(prefs.getString(MainActivity.Prefs.SERVER_PORT, ""));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Bad port: " + prefs.getString(MainActivity.Prefs.SERVER_PORT, null), e);
            return;
        }
        // Запуск соединения
        startConnection(new VpnConnection(
                this, mNextConnectionId.getAndIncrement(), server, port));
    }
    private void startConnection(final VpnConnection connection) {
        // Заменить существующее подключение новым
        final Thread thread = new Thread(connection, "VpnThread");
        setConnectingThread(thread);
        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent);
        connection.setOnEstablishListener(new VpnConnection.OnEstablishListener() {
            public void onEstablish(ParcelFileDescriptor tunInterface, String message) {
                if(message.equals("Error"))
                    mHandler.sendEmptyMessage(R.string.Errors);
                else{
                    mHandler.sendEmptyMessage(R.string.connected);
                    mConnectingThread.compareAndSet(thread, null);
                    setConnection(new Connection(thread, tunInterface));
                }

            }
        });
        thread.start();
    }
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
            } catch (IOException e) {
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
