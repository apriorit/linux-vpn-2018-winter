package com.example.admin.myapplication;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * Created by Admin on 17.02.2018.
 */

public class MyVpn extends VpnService {
    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    //a. Configure a builder for the interface.
    Builder builder = new Builder();

    // Services interface
    //onStartCommand - выполняется после StartService.Метод обратного вызова
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start a new session by creating a new thread.
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //a. Configure the TUN and get the interface.
                    mInterface = builder.setSession("MyVPNService")
                            .addAddress("192.168.0.1", 24)
                            .addDnsServer("8.8.8.8")
                            .addRoute("0.0.0.0", 0).establish();
                    //b. Packets to be sent are queued in this input stream.
                    FileInputStream in = new FileInputStream(
                            mInterface.getFileDescriptor());
                    //b. Packets received need to be written to this output stream.
                    FileOutputStream out = new FileOutputStream(
                            mInterface.getFileDescriptor());
                    //c. The UDP channel can be used to pass/get ip package to/from server
                    DatagramChannel tunnel = DatagramChannel.open();
                    // Connect to the server, localhost is used for demonstration only.
                    tunnel.connect(new InetSocketAddress("127.0.0.1", 80));
                    //d. Protect this socket, so package send by it will not be feedback to the vpn service.
                    protect(tunnel.socket());
                    //e. Use a loop to pass packets.

                    // Allocate the buffer for a single packet.
                    ByteBuffer packet = ByteBuffer.allocate(Short.MAX_VALUE);
                    // Timeouts:
                    //   - when data has not been sent in a while, send empty keepalive messages.
                    //   - when data has not been received in a while, assume the connection is broken.
                  //  Разкомментировать long lastSendTime = System.currentTimeMillis();
                    //long lastReceiveTime = System.currentTimeMillis();
                    // We keep forwarding packets till something goes wrong.
                    while (true) {
                        // Assume that we did not make any progress in this iteration.
                        boolean idle = true;
                        // Read the outgoing packet from the input stream.
                        int length = in.read(packet.array());
                        if (length > 0) {
                            // Write the outgoing packet to the tunnel.
                            packet.limit(length);
                            tunnel.write(packet);
                            packet.clear();
                            // There might be more outgoing packets.
                            idle = false;
                           // lastReceiveTime = System.currentTimeMillis();
                        }
                       // Read the incoming packet from the tunnel.
                        length = tunnel.read(packet);
                        if (length > 0) {
                            // Ignore control messages, which start with zero.
                            if (packet.get(0) != 0) {
                                // Write the incoming packet to the output stream.
                                out.write(packet.array(), 0, length);
                            }
                            packet.clear();
                            // There might be more incoming packets.
                            idle = false;
                            //lastSendTime = System.currentTimeMillis();
                        }
                    }

                } catch (Exception e) {
                    // Catch any exception
                    e.printStackTrace();
                } finally {
                    try {
                        if (mInterface != null) {
                            mInterface.close();
                            mInterface = null;
                        }
                    } catch (Exception e) {

                    }
                }
            }

        }, "MyVpnRunnable");

        //start the service
        mThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        if (mThread != null) {
            mThread.interrupt();
        }
        super.onDestroy();
    }
}
