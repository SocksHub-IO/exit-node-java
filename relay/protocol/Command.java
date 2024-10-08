package relay.protocol;

public enum Command {
    CONNECT((byte) 0x01),
    BIND((byte) 0x02),
    UDP_ASSOCIATE((byte) 0x03),
    UNKNOWN((byte) 0x00);

    private final byte version;

    Command(byte version) {
        this.version = version;
    }

    public byte getByte() {
        return this.version;
    }

    public static Command valueOf(byte version) {
        switch(version) {
            case 0x01:
                return CONNECT;
            case 0x02:
                return BIND;
            case 0x03:
                return UDP_ASSOCIATE;
            default:
                return UNKNOWN;
        }
    }
}
