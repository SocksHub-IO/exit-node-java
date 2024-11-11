package relay.communication;

public class ConnectionContext {
    private final byte[] connectBytes;
    private final byte[] sessionIdBytes;
    private final long sessionId;

    public ConnectionContext(byte[] connectBytes, byte[] sessionIdBytes, long sessionId) {
        this.connectBytes = connectBytes;
        this.sessionIdBytes = sessionIdBytes;
        this.sessionId = sessionId;
    }

    public byte[] getConnectBytes() {
        return this.connectBytes;
    }

    public byte[] getSessionIdBytes() {
        return this.sessionIdBytes;
    }

    public long getSessionId() {
        return sessionId;
    }
}