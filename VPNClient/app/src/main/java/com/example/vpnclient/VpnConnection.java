package com.example.vpnclient;

import static java.nio.charset.StandardCharsets.US_ASCII;
import android.app.PendingIntent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;

public class VpnConnection implements Runnable {
    /**
     *  Интерфейс обратного вызова, позволяющий {@link MainActivity} узнавать о новых подключениях
         *  и обновлять уведомление переднего плана с состоянием соединения.
     */
    public interface OnEstablishListener {
        void onEstablish(ParcelFileDescriptor tunInterface);
    }
    /** Максимальный размер пакета, ограниченный  MTU, который представлен, как signed short. */
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    /** Время, которое мы подождем между потерей соединения и повторной попыткой подсоединения. */
    private static final long RECONNECT_WAIT_MS = TimeUnit.SECONDS.toMillis(3);
    /** Время между поддержанием соединения, в том случае если интернет отсутствует.
     *
     * TODO: лучше так не делать, а вместо этого отключить vpn-соединение, когда будет нужно
     * пользователь сам его запустит.
     **/
    private static final long KEEPALIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15);
    /** Время, чтобы подождать без получения ответа, прежде чем решим, что сервер отключился. */
    private static final long RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);
    /**
     * Time between polling the VPN interface for new traffic, since it's non-blocking.
     * Время между опросом VPN интерфейса  для нового трафика, c тех пор как оно не заблокировано.
     * TODO: really don't do this; a blocking read on another thread is much cleaner.
     */
    private static final long IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100);
    /**
     * Number of periods of length {@IDLE_INTERVAL_MS} to wait before declaring the handshake a
     * complete and abject failure.
     *
     * TODO: use a higher-level protocol; hand-rolling is a fun but pointless exercise.
     */
    private static final int MAX_HANDSHAKE_ATTEMPTS = 50;
    private final VpnService mService;
    private final int mConnectionId;
    private final String mServerName;
    private final int mServerPort;
    private final Key mPublicKey;
    private final Key mPrivateKey;
    private int Idendificator;
    private Key mServerKey;
    private PendingIntent mConfigureIntent;
    private OnEstablishListener mOnEstablishListener;
    public VpnConnection(final VpnService service, final int connectionId,
                         final String serverName, final int serverPort) {
        mService = service;
        mConnectionId = connectionId;
        mServerName = serverName;
        mServerPort= serverPort;
        Key publicKey = null;
        Key privateKey = null;
        //Java is not C++, there is a garbage collector, so i can do it
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(1024);
            KeyPair kp = kpg.genKeyPair();
            publicKey = kp.getPublic();
            privateKey = kp.getPrivate();
        } catch (Exception e) {
            Log.e("Crypto", "RSA key pair error");
        }
        mPublicKey = publicKey;
        mPrivateKey = privateKey;
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
            // If anything needs to be obtained using the network, get it now.
            // This greatly reduces the complexity of seamless handover, which
            // tries to recreate the tunnel without shutting down everything.
            // In this demo, all we need to know is the server address.
            final SocketAddress serverAddress = new InetSocketAddress(mServerName, mServerPort);
            // We try to create the tunnel several times.
            // TODO: The better way is to work with ConnectivityManager, trying only when the
            //       network is available.
            // Here we just use a counter to keep things simple.
            for (int attempt = 0; attempt < 10; ++attempt) {
                // Reset the counter if we were connected.
                if (run(serverAddress)) {
                    attempt = 0;
                }
                // Sleep for a while. This also checks if we got interrupted.
                Thread.sleep(3000);
            }
            Log.i(getTag(), "Giving up");
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            Log.e(getTag(), "Connection failed, exiting", e);
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

            // Authenticate and configure the virtual network interface.
            iface = handshake(tunnel);
            // Now we are connected. Set the flag.
            connected = true;
            // Packets to be sent are queued in this input stream.
            FileInputStream in = new FileInputStream(iface.getFileDescriptor());
            // Packets received need to be written to this output stream.
            FileOutputStream out = new FileOutputStream(iface.getFileDescriptor());
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
                    // Write the outgoing packet to the tunnel.
                    packet.limit(length);
                    tunnel.write(packet);
                    packet.clear();
                    // There might be more outgoing packets.
                    idle = false;
                    lastReceiveTime = System.currentTimeMillis();
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
                    lastSendTime = System.currentTimeMillis();
                }
                // If we are idle or waiting for the network, sleep for a
                // fraction of time to avoid busy looping.
                if (idle) {
                    Thread.sleep(IDLE_INTERVAL_MS);
                    final long timeNow = System.currentTimeMillis();
                    if (lastSendTime + KEEPALIVE_INTERVAL_MS <= timeNow) {
                        // We are receiving for a long time but not sending.
                        // Send empty control messages.
                        packet.put((byte) 0).limit(1);
                        for (int i = 0; i < 3; ++i) {
                            packet.position(0);
                            tunnel.write(packet);
                        }
                        packet.clear();
                        lastSendTime = timeNow;
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
     /*    To build a secured tunnel, we should perform mutual authentication
         and exchange session keys for encryption.
         Our handshaking protocol:
        Identification:
        Client: 00
        Server : 0i[Identificator]
        Authentication:
         //exchange keys
         //mutual authentication
         Allocate the buffer for handshaking. We have a hardcoded maximum
        handshake size of 1024 bytes, which should be enough for demo
         purposes.*/

     //1. Send request to connect a new client
        ByteBuffer packet = ByteBuffer.allocate(1024);
        // Control messages always start with zero.
        packet.put((byte) 0).put("NewClient".getBytes()).flip();
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
            int length = tunnel.read(packet);
            if (length > 0 && packet.get(0) == 0) {
                setIdendificator(new String(packet.array(), 1, length - 1, US_ASCII).trim());
                //3. Send key
                sendKey(tunnel);
                //4.receiveParameters
                String parameters = receiveParameters(tunnel);
                return configure(parameters);
            }
        }
        throw new IOException("Timed out");
    }
    private void setIdendificator(String strInd) throws IllegalArgumentException
    {
        String[] fields = strInd.split(",");
        try {
            if (fields[0].charAt(0) == 'i') {
                Idendificator = Integer.parseInt(fields[1]);
            }
        }
     catch (NumberFormatException e) {
        throw new IllegalArgumentException("Bad identificator.");
    }
    }
    private void sendKey(DatagramChannel tunnel) throws IOException,InterruptedException
    {
        ByteBuffer packet = ByteBuffer.allocate(1024);
        //перепроверить ToString
        packet.put((byte) 0).put(BigInteger.valueOf(Idendificator).toByteArray()).put(",k,".getBytes()).put(mPublicKey.getEncoded()).flip();
        // Send the secret several times in case of packet loss.
        for (int i = 0; i < 3; ++i) {
            packet.position(0);
            tunnel.write(packet);
        }
    }
    private String receiveParameters(DatagramChannel tunnel) throws IOException,InterruptedException
    {
        ByteBuffer packet = ByteBuffer.allocate(1024);
        for (int i = 0; i < 6; ++i) {
            Thread.sleep(IDLE_INTERVAL_MS);
            int length = tunnel.read(packet);
            if (length > 0 && packet.get(0) == 0&&packet.get(1)=='m') {
                return (new String(packet.array(), 1, length - 1, US_ASCII).trim());
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
                    case 's':
                        builder.addSearchDomain(fields[1]);
                        break;
                    case 'k':
                    {
                        try {
                            X509EncodedKeySpec ks = new X509EncodedKeySpec(fields[1].getBytes());
                            KeyFactory kf = KeyFactory.getInstance("RSA");
                            mServerKey = kf.generatePublic(ks);
                        }
                        catch(NoSuchAlgorithmException e)
                        { throw new IllegalArgumentException(" Bad algorithm");}
                        catch (InvalidKeySpecException e)
                        {throw new IllegalArgumentException(" Bad key");}
                    }
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
                mOnEstablishListener.onEstablish(vpnInterface);
            }
        }
        Log.i(getTag(), "New interface: " + vpnInterface + " (" + parameters + ")");
        return vpnInterface;
    }
    private final String getTag() {
        return VpnConnection.class.getSimpleName() + "[" + mConnectionId + "]";
    }
}