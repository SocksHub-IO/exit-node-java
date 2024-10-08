package relay.protocol;

public enum Protocol {
    SOCKS5((byte) 0x05),
    SOCKS4((byte) 0x04),
    HTTP((byte) 0x03),
    UNKNOWN((byte) 0x00);

    private final byte version;

    Protocol(byte version) {
        this.version = version;
    }

    public byte toByte() {
        return this.version;
    }

    public static Protocol valueOf(byte version) {
        switch(version) {
            case 0x05:
                return SOCKS5;
            case 0x04:
                return SOCKS4;
            case 0x03:
                return HTTP;
            default:
                return UNKNOWN;
        }
    }
}
