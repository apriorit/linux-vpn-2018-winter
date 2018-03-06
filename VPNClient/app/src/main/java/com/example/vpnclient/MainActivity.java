package com.example.vpnclient;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.example.myapplication.R;

public class MainActivity extends AppCompatActivity {
    public interface Prefs {
        String NAME = "connection";
        String SERVER_ADDRESS = "server.address";
        String SERVER_PORT = "server.port";
        String SHARED_SECRET = "shared.secret";
    }
    @Override

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Восстанавливаем сохраненные данные на экран
        final TextView serverAddress = (TextView) findViewById(R.id.address);
        final TextView serverPort = (TextView) findViewById(R.id.port);
        final TextView sharedSecret = (TextView) findViewById(R.id.secret);
        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        //Вывод на экран,ранее введенных данных
        serverAddress.setText(prefs.getString(Prefs.SERVER_ADDRESS, ""));
        serverPort.setText(prefs.getString(Prefs.SERVER_PORT, ""));
        sharedSecret.setText(prefs.getString(Prefs.SHARED_SECRET, ""));
        //При нажатии на кнопку "Connect", даныые в prefs перезаписываются
        findViewById(R.id.connect).setOnClickListener(v -> {
            prefs.edit()
                    .putString(Prefs.SERVER_ADDRESS, serverAddress.getText().toString())
                    .putString(Prefs.SERVER_PORT, serverPort.getText().toString())
                    .putString(Prefs.SHARED_SECRET, sharedSecret.getText().toString())
                    .commit();
            //Запрос на vpn подключение будет вызываться только один раз, при первом запуске
            // устройства
            Intent intent = VpnService.prepare(getApplicationContext());
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
        });
        findViewById(R.id.disconnect).setOnClickListener(v -> {
            startService(getServiceIntent().setAction(MyVpnService.ACTION_DISCONNECT));
        });
    }
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            startService(getServiceIntent().setAction(MyVpnService.ACTION_CONNECT));
        }
    }
    private Intent getServiceIntent() {
        return new Intent(this, MyVpnService.class);
    }

}
