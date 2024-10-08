package relay.exception;

import relay.protocol.ReplyCode;

public class Socks5Exception extends Exception {

    private final byte errorCode;

    public Socks5Exception(String message, ReplyCode.SOCKS5 replyCode) {
        super(message);
        this.errorCode = replyCode.toByte();
    }

    public byte getErrorCode() {
        return this.errorCode;
    }
}
