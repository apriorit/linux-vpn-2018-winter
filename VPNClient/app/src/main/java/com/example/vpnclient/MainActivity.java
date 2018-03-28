package com.example.vpnclient;


import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.vpnclient.R;

public class MainActivity extends AppCompatActivity {

    public interface Prefs {
        String NAME = "connection";
        String SERVER_ADDRESS = "server.address";
        String SERVER_PORT = "server.port";
    }

    private void activateButton(Button butt)
    {
        //activate button
        butt.setClickable(true);
        butt.setVisibility(View.VISIBLE);
    }
    private void deactivateButton(Button butt)
    {
        //deactivate button "on"
        butt.setClickable(false);
        butt.setVisibility(View.GONE);
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //get buttons
        Button buttDisconnect = findViewById(R.id.disconnect);
        Button buttConnect = findViewById(R.id.connect);

        deactivateButton(buttDisconnect);

        //create objs for address, port
        final TextView serverAddress =  findViewById(R.id.address);
        final TextView serverPort = findViewById(R.id.port);
        //get saved settings from the last activity
        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        //display stored settings
        serverAddress.setText(prefs.getString(Prefs.SERVER_ADDRESS, ""));
        serverPort.setText(prefs.getString(Prefs.SERVER_PORT, ""));
        //if call "connect"
       buttConnect.setOnClickListener(v -> {
            deactivateButton(buttConnect);
            activateButton(buttDisconnect);
            //rewrite stored data, after call "connect"
            prefs.edit()
                    .putString(Prefs.SERVER_ADDRESS, serverAddress.getText().toString())
                    .putString(Prefs.SERVER_PORT, serverPort.getText().toString())
                    .commit();
            //The request on VPN connection will be called only once, at the first using application
            Intent intent = VpnService.prepare(getApplicationContext());
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
        });

        buttDisconnect.setOnClickListener(v -> {
            activateButton(buttConnect);
            deactivateButton(buttDisconnect);
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
