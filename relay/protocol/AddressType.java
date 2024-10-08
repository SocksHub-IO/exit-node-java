package relay.protocol;

public enum AddressType {
    IP_V4((byte) 0x01, 4),
    DOMAINNAME((byte) 0x03, 0), // the address size is in the next byte of the packet data
    IP_V6((byte) 0x04, 16),
    UNKNOWN((byte) 0x00, 0);

    private final byte type;
    private final int size;

    AddressType(byte type, int size) {
        this.type = type;
        this.size = size;
    }

    public byte toByte() {
        return this.type;
    }

    public static AddressType valueOf(byte type) {
        switch(type) {
            case 0x01:
                return IP_V4;
            case 0x03:
                return DOMAINNAME;
            case 0x04:
                return IP_V6;
            default:
                return UNKNOWN;
        }
    }

    public int getSize() {
        return this.size;
    }
}
