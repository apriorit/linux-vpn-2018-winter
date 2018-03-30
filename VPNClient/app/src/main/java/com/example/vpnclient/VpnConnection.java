package com.example.vpnclient;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.app.PendingIntent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;


import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class VpnConnection implements Runnable {
    /**
     * Callback interface to let the {@link MyVpnService} know about new connections
     * and update the foreground notification with connection status.
     */
    public interface OnEstablishListener {
        void onEstablish(ParcelFileDescriptor tunInterface,String message);
    }
    /** Maximum packet size is constrained by the MTU, which is given as a signed short. */
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    /** Time between keepalives if there is no traffic at the moment.
     *
     * TODO: don't do this; it's much better to let the connection die and then reconnect when
     *       necessary instead of keeping the network hardware up for hours on end in between.
     **/
    private static final long KEEPALIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15);
    /** Time to wait without receiving any response before assuming the server is gone. */
    private static final long RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);
    /**
     * Time between polling the VPN interface for new traffic, since it's non-blocking.
     *
     * TODO: really don't do this; a blocking read on another thread is much cleaner.
     */
    private static final long IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100);
    /**
     * Number of periods of length {@IDLE_INTERVAL_MS} to wait before declaring the handshake a
     * complete and abject failure.
     */
    private static final int MAX_HANDSHAKE_ATTEMPTS = 50;
    private final VpnService mService;
    private final int mConnectionId;
    private final String mServerName;
    private final int mServerPort;
    private CryptoClient myCrypto;
    private PendingIntent mConfigureIntent;
    private OnEstablishListener mOnEstablishListener;
    public VpnConnection(final VpnService service, final int connectionId,
                         final String serverName, final int serverPort) {
        mService = service;
        mConnectionId = connectionId;
        mServerName = serverName;
        mServerPort= serverPort;
        myCrypto = new CryptoClient();

    }

    /**
     * Optionally, set an intent to configure the VPN. This is {@code null} by default.
     */
    public void setConfigureIntent(PendingIntent intent) {
        mConfigureIntent = intent;
    }
    public void setOnEstablishListener(OnEstablishListener listener) {
        mOnEstablishListener = listener;
    }
    @Override
    public void run() {
        try {
            Log.i(getTag(), "Starting");

            final SocketAddress serverAddress = new InetSocketAddress(mServerName, mServerPort);
            // We try to create the tunnel several times.
            // TODO: The better way is to work with ConnectivityManager, trying only when the
            //       network is available.
            // Here we just use a counter to keep things simple.
            for (int attempt = 0; attempt < 1; ++attempt) {
                // Reset the counter if we were connected.
                if (run(serverAddress)) {
                    attempt = 0;
                }
                // Sleep for a while. This also checks if we got interrupted.
                Thread.sleep(3000);
            }
            Log.i(getTag(), "Giving up");
            mOnEstablishListener.onEstablish(null,"Timeout");
        }
        catch(java.nio.channels.ClosedByInterruptException e)
        {
            Log.e(getTag(), "User disconnect connection", e);
        }
        catch(InterruptedException e)
        {
        Log.e(getTag(), "My connection was interrupted", e);}
        catch (IOException  | IllegalArgumentException e) {
               mOnEstablishListener.onEstablish(null,"Error");
            Log.e(getTag(), "Connection failed, exiting. Something in program goes wrong", e);
        }
    }
    private boolean run(SocketAddress server)
            throws IOException, InterruptedException, IllegalArgumentException {

        ParcelFileDescriptor iface = null;
        boolean connected = false;
        // Create a DatagramChannel as the VPN tunnel.
        try (DatagramChannel tunnel = DatagramChannel.open()) {
            // Protect the tunnel before connecting to avoid loopback.
            if (!mService.protect(tunnel.socket())) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }
            // Connect to the server.
            tunnel.connect(server);
            // For simplicity, we use the same thread for both reading and
            // writing. Here we put the tunnel into non-blocking mode.
            tunnel.configureBlocking(false);
            //create keys for data protection
            myCrypto.generateKeys();
            // Authenticate and configure the virtual network interface.
            iface = handshake(tunnel);
            // Now we are connected. Set the flag.
            connected = true;
            // Packets to be sent are queued in this input stream
            FileInputStream in = new FileInputStream(iface.getFileDescriptor());
            // Packets received need to be written to this output stream.
            FileOutputStream out = new FileOutputStream(iface.getFileDescriptor());
            out.flush();
            // Allocate the buffer for a single packet.
            ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);
            // Timeouts:
            //   - when data has not been sent in a while, send empty keepalive messages.
            //   - when data has not been received in a while, assume the connection is broken.
            long lastSendTime = System.currentTimeMillis();
            long lastReceiveTime = System.currentTimeMillis();
            // We keep forwarding packets till something goes wrong.
            while (true) {
                // Assume that we did not make any progress in this iteration.
                boolean idle = true;
                // Read the outgoing packet from the input stream.
                int length = in.read(packet.array());
                if (length > 0) {
                    //encrypt data by AES
                    byte [] encrypt = myCrypto.encryptAES(Arrays.copyOfRange(packet.array(),0,length));
                    packet.clear();
                    packet.put((byte)2).put(encrypt);
                    packet.position(0);
                    // Write the outgoing packet to the tunnel.
                    packet.limit(encrypt.length+1);
                    tunnel.write(packet);
                    packet.clear();
                    // There might be more outgoing packets.
                    idle = false;
                    lastReceiveTime = System.currentTimeMillis();
                }
                idle = true;
                // Read the incoming packet from the tunnel.
                length = tunnel.read(packet);
                if (length > 0) {
                    // Ignore other control message, reveive only message which starts with 2.
                    if (packet.get(0) == 2 ) {
                       byte[] decrypt =  myCrypto.decryptAES(Arrays.copyOfRange(packet.array(),1,length));
                        // Write the incoming packet to the output stream.
                            out.write(decrypt, 0, decrypt.length);
                    }
                    packet.clear();
                    // There might be more incoming packets.
                    idle = false;
                    lastSendTime = System.currentTimeMillis();
                }
                // If we are idle or waiting for the network, sleep for a
                // fraction of time to avoid busy looping.
                if (idle) {
                    Thread.sleep(IDLE_INTERVAL_MS);
                    final long timeNow = System.currentTimeMillis();
                    if (lastSendTime + KEEPALIVE_INTERVAL_MS <= timeNow) {
                        /*// We are receiving for a long time but not sending.
                                // Send empty control messages.
                                        packet.put((byte)0).limit(1);
                        for (int i = 0; i < 3; ++i) {
                            packet.position(0);
                            tunnel.write(packet);
                        }
                        packet.clear();
                        lastSendTime = timeNow;*/
                        throw new IllegalStateException("Timed out");
                    } else if (lastReceiveTime + RECEIVE_TIMEOUT_MS <= timeNow) {
                        // We are sending for a long time but not receiving.
                        throw new IllegalStateException("Timed out");
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(getTag(), "Cannot use socket", e);
        } finally {
            if (iface != null) {
                try {
                    iface.close();
                } catch (IOException e) {
                    Log.e(getTag(), "Unable to close interface", e);
                }
            }
        }
        return connected;
    }
    private ParcelFileDescriptor handshake(DatagramChannel tunnel)
            throws IOException, InterruptedException {


     //1. Send request to connect a new client
        ByteBuffer packet = ByteBuffer.allocate(1024);
        // Control messages always start with zero.
        packet.put( (byte)0).put("NewClient".getBytes()).put(myCrypto.getPublicRSAKey()).flip();
        // Send the request several times in case of packet loss.
        for (int i = 0; i < 3; ++i) {
            packet.position(0);
            tunnel.write(packet);
        }
        packet.clear();
        //2. Receive identificator
        // Wait for the identificator within a limited time.
        for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; ++i) {
            Thread.sleep(IDLE_INTERVAL_MS);
            // Normally we should not receive random packets. Check that the first
            // byte is 0 as expected.
            packet.clear();
            int length = tunnel.read(packet);
            if (length > 0 && packet.get(0) == 0) {
                String msgFromServer = new String(packet.array(), 1, length - 1,"UTF-8").trim();
                if(msgFromServer.equals("Error"))
                    throw new IOException("Server did not allow connection");
                if(msgFromServer.charAt(0)=='k') {
                    setData(Arrays.copyOfRange(packet.array(),2,length));
                    //3. Send key
                    sendKey(tunnel);
                    //4.receiveParameters
                    String parameters = receiveParameters(tunnel);
                    return configure(parameters);
                }
            }
        }
        throw new IOException("Timed out");
    }
    private void setData(byte[] str) throws IllegalArgumentException {
        try {
            myCrypto.setServerPublicRSAKey(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad server key.");
            }
        }

    private void sendKey(DatagramChannel tunnel) throws IOException,InterruptedException
    {
        ByteBuffer packet = ByteBuffer.allocate(1024);
        packet.put((byte)1).put("aes".getBytes()).put(myCrypto.ecryptRSA(myCrypto.getAESKey())).flip();
        // Send the secret several times in case of packet loss.
        for (int i = 0; i < 3; ++i) {
            packet.position(0);
            tunnel.write(packet);
        }
    }
    private String receiveParameters(DatagramChannel tunnel) throws IOException,InterruptedException
    {
        ByteBuffer packet = ByteBuffer.allocate(1024);
        for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; ++i) {
            Thread.sleep(IDLE_INTERVAL_MS);
            packet.clear();
            int length = tunnel.read(packet);
            Log.d(getTag(),packet.toString());
            if (length > 0 && packet.get(0) == 1&&packet.get(1)=='p'){
                byte  decrypt[]  = myCrypto.decryptRSA(Arrays.copyOfRange(packet.array(),2,length));
                     return (new String(decrypt,"UTF-8"));
            }
        }
        throw new IOException("Timed out");
    }
    private ParcelFileDescriptor configure(String parameters) throws IllegalArgumentException {
        // Configure a builder while parsing the parameters.
        VpnService.Builder builder = mService.new Builder();
        for (String parameter : parameters.split(" ")) {
            String[] fields = parameter.split(",");
            try {
                switch (fields[0].charAt(0)) {
                    case 'm':
                        builder.setMtu(Short.parseShort(fields[1]));
                        break;
                    case 'a':
                        builder.addAddress(fields[1], Integer.parseInt(fields[2]));
                        break;
                    case 'r':
                        builder.addRoute(fields[1], Integer.parseInt(fields[2]));
                        break;
                    case 'd':
                        builder.addDnsServer(fields[1]);
                        break;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad parameter: " + parameter);
            }
        }
        // Create a new interface using the builder and save the parameters.
        final ParcelFileDescriptor vpnInterface;
        synchronized (mService) {
            vpnInterface = builder
                    .setSession(mServerName)
                    .setConfigureIntent(mConfigureIntent)
                    .establish();
            if (mOnEstablishListener != null) {
                mOnEstablishListener.onEstablish(vpnInterface,"MyVPN is connected!");
            }
        }
        Log.i(getTag(), "New interface: " + vpnInterface + " (" + parameters + ")");
        return vpnInterface;
    }
    private final String getTag() {
        return VpnConnection.class.getSimpleName() + "[" + mConnectionId + "]";
        }
        }
