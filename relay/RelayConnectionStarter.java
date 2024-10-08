package relay;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import relay.communication.RelayCommunicationHandler;

public class RelayConnectionStarter {

    static private int relayPort;
    static private String relayIP;

    public static void init(String relayIP, int relayPort){
        RelayConnectionStarter.relayIP = relayIP;
        RelayConnectionStarter.relayPort = relayPort;
        openRelayConnection();
    }

    public static void openRelayConnection(){
        try{
            SSLContext sslContext = SSLContext.getDefault();
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            final SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket();
            socket.setReuseAddress(true);
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(relayIP, relayPort), 4000);
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
            socket.setEnabledCipherSuites(new String[]{
                    "TLS_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_RSA_WITH_AES_128_GCM_SHA256"
            });

            Thread proxyHandlerThread = new Thread(new RelayCommunicationHandler(socket));
            proxyHandlerThread.setPriority(Thread.MAX_PRIORITY);
            proxyHandlerThread.start();
        }catch (Exception e){
            e.printStackTrace();
            System.exit(73);
        }
    }
}
