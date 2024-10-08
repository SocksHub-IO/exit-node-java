package relay.protocol;

public class ReplyCode {
    public static enum SOCKS5 {
        SUCCEEDED((byte) 0x00),
        GENERAL_SOCKS_SERVER_FAILURE((byte) 0x01),
        CONNECTION_NOT_ALLOWED_BY_RULESET((byte) 0x02),
        NETWORK_UNREACHABLE((byte) 0x03),
        HOST_UNREACHABLE((byte) 0x04),
        CONNECTION_REFUSED((byte) 0x05),
        TTL_EXPIRED((byte) 0x06),
        COMMAND_NOT_SUPPORTED((byte) 0x07),
        ADDRESS_TYPE_NOT_SUPPORTED((byte) 0x08),
        UNKNOWN((byte) 0x09);

        private final byte code;

        SOCKS5(byte code) {
            this.code = code;
        }

        public byte toByte() {
            return this.code;
        }
    }
}
