package relay.communication;

import static relay.protocol.ReplyCode.SOCKS5.CONNECTION_REFUSED;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import relay.exception.DestinationConnectException;
import relay.exception.DestinationIOException;
import relay.exception.RelayIOException;
import relay.exception.Socks5Exception;
import relay.protocol.Protocol;
import relay.protocol.Socks5;

public class RelayCommunicationHandler implements Runnable {

    private static final Queue<RegistrationRequest> registrationQueue = new ConcurrentLinkedQueue<>();
    private static final Map<Long, SocketChannel> connectionsMapper = new HashMap<>();

    private final Socket relaySocket;
    private Selector selector;

    public RelayCommunicationHandler(Socket relaySocket) {
        this.relaySocket = relaySocket;
    }

    public void run() {
        System.out.println("Proxy Started.");

        /* Starting the thread that reads data from the destination servers and sends it back
        *  to the relay */
        Thread t = new Thread(){
            @Override
            public void run() {
                try{
                    selector = Selector.open();
                }catch (Exception e){
                    e.printStackTrace();
                    System.exit(1);
                }
                writeToRelay();
            }
        };
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();

        try{
            readFromRelay();
        } catch (Exception e){
            System.err.println(e.getMessage());
            System.exit(3);
        } finally {
            close();
        }
    }

    public void readFromRelay() throws RelayIOException {

        while(relaySocket.isConnected()) {
            InputStream inputStream;

            try {
                inputStream = relaySocket.getInputStream();
            } catch (IOException ioe) {
                throw new RelayIOException(ioe.getMessage());
            }

            byte[] idBytes = new byte[8];
            if (!Utils.readExactly(inputStream, idBytes, 8)) {
                System.err.println("[] invalid packet or connection closed");
                throw new RelayIOException("Could not read the initial 6 bytes from the relay");
            }

            // Extract remote ID string from the metadata
            long remoteId = Utils.extractRemoteId(idBytes);
            System.out.println("[" + remoteId + "]" + " received packet");

            // 2. Read the next 2 bytes for the length of the data from the metadata
            byte[] lengthBytes = new byte[2];
            if (!Utils.readExactly(inputStream, lengthBytes, 2)) {
                System.err.println("[" + remoteId + "]" + " invalid packet length or connection closed");
                throw new RelayIOException("Could not read the 2 length bytes from the relay");
            }

            // Convert length bytes to an integer
            int payloadLength = ByteBuffer.wrap(lengthBytes).getShort() & 0xFFFF;
            System.out.println("[" + remoteId + "]" + " data length: " + payloadLength);

            /* If the data length is 0, this is a close packet, which means the SOCKS client has
            * closed the connection to the relay, so the connection to the destination server
            * must also be closed */
            if(payloadLength == 0){
                if (connectionsMapper.containsKey(remoteId)) {
                    SocketChannel socketChannel = connectionsMapper.get(remoteId);
                    try {
                        if(socketChannel != null){
                            socketChannel.close();
                        }
                        System.out.println(remoteId + "]-CLOSE closed socket channel");
                    } catch (Exception e) {
                        System.err.println("[" + remoteId + "]-CLOSE-ERROR error closing socket channel: " + e.getMessage());
                    }
                }
                continue;
            }

            // 3. Read the data based on the extracted length
            byte[] data = new byte[payloadLength];
            if (!Utils.readExactly(inputStream, data, payloadLength)) {
                System.err.println("[" + remoteId + "]" + " failed to read the full data or connection closed");
                throw new RelayIOException("Could not read the data bytes from the relay");
            }

            if (connectionsMapper.containsKey(remoteId)) {
                /* EXISTING SESSION
                *  the connection must have been established, so the data just needs to be forwarded
                *  to the destination server */
                try {
                    SocketChannel socketChannel = connectionsMapper.get(remoteId);
                    int written = 0;
                    do {
                        written += socketChannel.write(ByteBuffer.wrap(data));
                        System.out.println(remoteId + "]-FORWARD forwarded " + written + " from " + payloadLength);
                    } while (written < payloadLength);
                } catch (ClosedChannelException cce) {
                    // TODO: send close packet back
                } catch (Exception e) {
                    System.err.println("[" + remoteId + "]-FORWARD error forwarding message(" + payloadLength + "): " + e.getMessage());
                }
            } else {
                /* NEW SESSION
                *  the SOCKS 5 request must be evaluated and a new connection to the destination
                *  must be established */
                Protocol protocol = Protocol.valueOf(data[0]);

                try {
                    switch (protocol) {
                        case SOCKS5:
                            InetSocketAddress inetSocketAddress = Socks5.evaluateRequest(data);
                            System.out.println("[" + remoteId + "]-CONNECT accepted " + protocol + " request");
                            connectToServer(data, inetSocketAddress, idBytes, remoteId);
                            break;
                        default:
                            System.err.println("[" + remoteId + "]-CONNECT invalid protocol version");
                            // TODO: send back a close packet to the relay
                            break;
                    }
                } catch (Socks5Exception s5e) {
                    System.err.println("[" + remoteId + "]-CONNECT Socks5Exception: " + s5e.getMessage());
                    // TODO: to send back the error code create a buffer as an object field and add a block to the writeToRelay method to write it back to the client if the buffer is not empty
                }
            }

            Thread.yield();
        }
    }

    private void connectToServer(byte[] connectBytes,
                                 InetSocketAddress inetSocketAddress,
                                 byte[] idBytes,
                                 long clientSessionId) {
        // Create a context to hold the state
        ConnectionContext context = new ConnectionContext(connectBytes, idBytes, clientSessionId);

        // Create a registration request
        RegistrationRequest registrationRequest = new RegistrationRequest(context,
                inetSocketAddress);
        registrationQueue.add(registrationRequest);
        selector.wakeup();
    }

    private void writeToRelay() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16384);
        while (true) {
            try {
                selector.select(); // Blocks until an event occurs

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectedKeys.iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    ConnectionContext connectionContext = (ConnectionContext) key.attachment();
                    long clientId = connectionContext.getSessionId();
//                    System.out.println("[" + clientId + "]-SELECTOR next");

                    // means connection with target is closed
                    if (!key.isValid()) {
                        System.out.println("[" + clientId + "]-SELECTOR invalid");
                        sendBackClose(connectionContext.getSessionIdBytes(),
                                connectionContext.getSessionId());
                        continue;
                    }

                    if (key.isConnectable()) {
//                        System.out.println("[" + clientId + "]-SELECTOR connectable");

                        try {
                            handleDestinationConnect(key);
                        } catch (DestinationConnectException e) {
                            sendBackConnectionFailed(connectionContext.getConnectBytes(),
                                    connectionContext.getConnectBytes().length,
                                    connectionContext.getSessionIdBytes(),
                                    connectionContext.getSessionId());
                        }
                    } else if (key.isReadable()) {
//                        System.out.println("[" + clientId + "]-SELECTOR readable");

                        try {
                            handleDestinationRead(key, buffer);
                        } catch (DestinationIOException e) {
                            sendBackClose(connectionContext.getSessionIdBytes(),
                                    connectionContext.getSessionId());
                        }
                    }
                }

                while (registrationQueue.peek() != null) {

                    RegistrationRequest registrationRequest = registrationQueue.remove();
                    ConnectionContext connectionContext = registrationRequest.getContext();

                    try{
                        SocketChannel socketChannel = SocketChannel.open();
                        socketChannel.configureBlocking(false);
                        socketChannel.connect(registrationRequest.getInetSocketAddress());
                        socketChannel.register(selector, SelectionKey.OP_CONNECT, connectionContext);
                        connectionsMapper.put(connectionContext.getSessionId(), socketChannel);
                    } catch (UnresolvedAddressException e) {
                        sendBackConnectionFailed(connectionContext.getConnectBytes(),
                                connectionContext.getConnectBytes().length,
                                connectionContext.getSessionIdBytes(),
                                connectionContext.getSessionId());
                    }
                }

            } catch (IOException e) {
                System.err.println("[]-REPLY-ERROR failed to reply to client: " + e.getMessage());
            } finally {
                Thread.yield();
            }
        }
    }

    private void handleDestinationConnect(SelectionKey key) throws DestinationConnectException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ConnectionContext context = (ConnectionContext) key.attachment();
        try{
            if(socketChannel.finishConnect()){
                socketChannel.register(selector, SelectionKey.OP_READ, key.attachment());
                context.getConnectBytes()[1] = 0;
                sendBack(context.getConnectBytes(),
                        context.getConnectBytes().length,
                        context.getSessionIdBytes());
                System.out.println("[" + context.getSessionId() + "]-CONNECT replied connect success: " + context.getConnectBytes().length + " bytes");
            } else {
                System.err.println("[" + context.getSessionId() + "]-CONNECT-ERROR failed to connect to forward server");
                key.cancel();
                socketChannel.close();
                throw new DestinationConnectException("failed to connect to forward server");
            }
        } catch (IOException ioe){
            key.cancel();
            try { socketChannel.close(); } catch (Exception ignored) {}
            System.err.println("[" + context.getSessionId() + "]-CONNECT-ERROR failed to connect to forward server: " + ioe.getMessage());
            throw new DestinationConnectException(ioe.getMessage());
        }
    }

    private void handleDestinationRead(SelectionKey key, ByteBuffer buffer) throws DestinationIOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionContext context = (ConnectionContext) key.attachment();

        try {
            int bytesRead = channel.read(buffer);
            buffer.flip();
            if (bytesRead <= 0) {
                key.cancel();
                channel.close();
                throw new DestinationIOException("server connection was closed");
            } else {
                byte[] data = new byte[buffer.limit()];
                buffer.get(data);
                sendBack(data, bytesRead, context.getSessionIdBytes());
//                System.out.println("[" + context.getClientID() + "]-REPLY replied to client " + bytesRead + " bytes");
            }
        } catch (IOException ioe) {
            key.cancel();
            try { channel.close(); } catch (Exception ignored) {}
            System.err.println("[" + context.getSessionId() + "]-REPLY-ERROR failed to reply to client: " + ioe.getMessage());
            throw new DestinationIOException(ioe.getMessage());
        } finally {
            buffer.clear();
        }
    }

    private void sendBack(byte[] bytes, int len, byte[] idBytes) {

        try {
            OutputStream outputStream = relaySocket.getOutputStream();

            // The size of the packet will be idBytes + 2 bytes for the length + the actual data length
            byte[] packet = new byte[idBytes.length + 2 + len];

            // Copy the ID (header) bytes into the packet
            System.arraycopy(idBytes, 0, packet, 0, idBytes.length);

            // Add the 2-byte length of the message
            packet[idBytes.length] = (byte) (len >> 8);  // High byte of length
            packet[idBytes.length + 1] = (byte) (len);   // Low byte of length

            // Copy the actual data (bytes) into the packet after the ID and length
            System.arraycopy(bytes, 0, packet, idBytes.length + 2, len);

            outputStream.write(packet, 0, packet.length);
            outputStream.flush();
        } catch (IOException ioe) {
            // FATAL: failed to write to the relay server
            System.exit(2);
        }
    }

    private void sendBackClose(byte[] idBytes, long clientId) {
        System.out.println("[" + clientId + "]-CLOSED connection to target was closed");
        sendBack(new byte[]{}, 0, idBytes);
    }

    private void sendBackConnectionFailed(byte[] bytes, int len, byte[] idBytes, long clientId) {
        System.out.println("[" + clientId + "]-CLOSED connection to target has failed");
        bytes[1] = CONNECTION_REFUSED.toByte();
        sendBack(bytes, len, idBytes);
    }

    private void close() {
        try {
            if (relaySocket != null) {
                relaySocket.close();
            }
        } catch (IOException e) {
            // ignore
        }

        System.out.println("Proxy Closed.");
    }
}