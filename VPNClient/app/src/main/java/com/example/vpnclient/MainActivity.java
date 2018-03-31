package com.example.vpnclient;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.example.vpnclient.R;

public class MainActivity extends AppCompatActivity {
    boolean connected = false;

    private static final String IPV4_REGEX = "\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z";
    private static final String emptyField = "Please, input data for connection!";
    private static final String errorMessage="Wrong format of IPV4 address!\nExample of correct address: 192.168.0.1";
    //interface fo saving settings
    public interface Prefs {
        String NAME = "connection";
        String SERVER_ADDRESS = "server.address";
        String SERVER_PORT = "server.port";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //create objs for address, port
        final TextView serverAddress =  findViewById(R.id.address);
        final TextView serverPort = findViewById(R.id.port);
        //get saved settings from the last activity
        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        //display stored settings
        serverAddress.setText(prefs.getString(Prefs.SERVER_ADDRESS, ""));
        serverPort.setText(prefs.getString(Prefs.SERVER_PORT, ""));
        Button myButton = findViewById(R.id.connect);

        myButton.setOnClickListener(v -> {
            if(!connected) //connect
                {
                    //get text from edittext
                    String textAddress = serverAddress.getText().toString();
                    String textPort = serverPort.getText().toString();
                    //if address or port is empty, show error
                    if (textAddress.equals("")|| textPort.equals(""))
                        callMessageError(emptyField);
                    else {
                        //if address is not correct, show error
                        if (!textAddress.matches(IPV4_REGEX))
                            callMessageError(errorMessage);
                        else {
                            connected=true;
                            myButton.setBackgroundResource(R.drawable.offbut_active);
                            //rewrite stored data, after call "connect"
                            prefs.edit().putString(Prefs.SERVER_ADDRESS, textAddress).putString(Prefs.SERVER_PORT, textPort).commit();
                            //try to run vpn
                            intentOnVpnConnection();
                        }
                    }
                }
                else //disconnect
                {
                    connected = false;
                    myButton.setBackgroundResource(R.drawable.onbut_active);
                    startService(getServiceIntent().setAction(MyVpnService.ACTION_DISCONNECT));
                }
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
    private void  callMessageError(String msg)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Error")
                .setMessage(msg)
                .setIcon(R.drawable.ic_errormsg)
                .setPositiveButton("OK",null)
                .show();
    }
    private void intentOnVpnConnection()
    {
        //The request on VPN connection will be called only once, at the first using application
        Intent intent = VpnService.prepare(getApplicationContext());
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }
}
