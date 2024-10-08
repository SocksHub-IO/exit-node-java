package relay.communication;

public class ConnectionContext {
    private final byte[] connectBytes;
    private final byte[] idBytes;
    private final String clientID;

    public ConnectionContext(byte[] connectBytes, byte[] idBytes, String clientID) {
        this.connectBytes = connectBytes;
        this.idBytes = idBytes;
        this.clientID = clientID;
    }

    public byte[] getConnectBytes() {
        return this.connectBytes;
    }

    public byte[] getIdBytes() {
        return this.idBytes;
    }

    public String getClientID() {
        return clientID;
    }
}