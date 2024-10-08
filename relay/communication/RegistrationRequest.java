package relay.communication;

import java.net.InetSocketAddress;

public class RegistrationRequest {
    private final ConnectionContext context;
    private final InetSocketAddress inetSocketAddress;

    public RegistrationRequest(ConnectionContext context, InetSocketAddress inetSocketAddress) {
        this.context = context;
        this.inetSocketAddress = inetSocketAddress;
    }

    public ConnectionContext getContext() {
        return this.context;
    }

    public InetSocketAddress getInetSocketAddress() {
        return this.inetSocketAddress;
    }
}
