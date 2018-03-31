package com.example.vpnclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Created by Admin on 13.03.2018.
 */

public class BootUpReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
            Intent i = new Intent(context, MyVpnService.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            while(!hasNetwork(context)){}
            context.startService(i.setAction(MyVpnService.ACTION_CONNECT));

    }
    private boolean hasNetwork(Context context)
    {   ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork!=null&&activeNetwork.isConnectedOrConnecting();
    }
}
