package relay.protocol;

import java.net.InetSocketAddress;
import relay.exception.Socks5Exception;

public class Socks5 {

    /**
     * This method evaluates a SOCKS 5 request as described at
     * <a href="https://datatracker.ietf.org/doc/html/rfc1928#section-4">https://datatracker.ietf.org/doc/html/rfc1928</a>,
     * section 4 and section 5. Section 3 is already handled on the relay server for efficiency
     * and security purposes (username and password can be provided by the SOCKS client).
     *
     * @param packet - the byte array that contains a socks 5 request
     * @return the {@link InetSocketAddress} of the destination extracted from the socks 5 request
     * @throws Socks5Exception - wrong socks 5 request payload
     */
    public static InetSocketAddress evaluateRequest(byte[] packet) throws Socks5Exception {
        // Tracks the current position in the byte array (packet data)
        int index = 0;

        // VER byte - SOCKS version is the 1st byte in the packet data
        // will skip it, as this point is already reached as a consequence of it being 0x05
        index++;

        // CMD byte - SOCKS command is the 2nd byte in the packet data
        Command socksCommand = Command.valueOf(packet[index++]);

        // Currently only supporting CONNECT command
        if (!socksCommand.equals(Command.CONNECT)) {
            throw new Socks5Exception("Unknown command", ReplyCode.SOCKS5.COMMAND_NOT_SUPPORTED);
        }

        // RSV byte - the reserved byte is the 3rd byte in the packet data
        // needs to be skipped
        index++;

        // ATYP byte - the address type byte is the 4th byte in the packet data
        AddressType addressType = AddressType.valueOf(packet[index++]);

        // Address type validation
        if(addressType.equals(AddressType.UNKNOWN) || addressType.equals(AddressType.IP_V6)) {
            throw new Socks5Exception("Address type not supported",
                    ReplyCode.SOCKS5.ADDRESS_TYPE_NOT_SUPPORTED);
        }

        // Determine the address size based on the address type
        int destinationAddressSize = addressType.getSize();

        // If the address type is domain name, the first of the address byte defines the length
        if (addressType.equals(AddressType.DOMAINNAME)) {
            destinationAddressSize = packet[index++];
        }

        // DST.ADDR bytes - the destination address bytes begin with the 5th byte
        byte[] destinationAddressBytes = new byte[destinationAddressSize];

        // Retrieve the destination address from the packet
        for (int i = 0; i < destinationAddressSize; i++) {
            destinationAddressBytes[i] = packet[index++];
        }

        // Convert destination address to string
        String destinationAddress = addressToString(addressType,
                destinationAddressBytes,
                destinationAddressSize);

        // DST.PORT bytes - the destination port bytes are the next 2 bytes after DST.ADDR
        byte[] destinationPortBytes = new byte[2];
        destinationPortBytes[0] = packet[index++];
        destinationPortBytes[1] = packet[index];

        // Convert destination port (2 bytes) to an integer for the port
        int destinationPort = ((destinationPortBytes[0] & 0xFF) << 8) |
                (destinationPortBytes[1] & 0xFF);

        return new InetSocketAddress(destinationAddress, destinationPort);
    }

    private static String addressToString(AddressType addressType,
                                               byte[] destinationAddress,
                                               int destinationAddressSize) throws Socks5Exception {

        // Convert destination address bytes to string (either IP or domain)
        if (addressType.equals(AddressType.IP_V4)) {
            return (destinationAddress[0] & 0xFF) + "." +
                    (destinationAddress[1] & 0xFF) + "." +
                    (destinationAddress[2] & 0xFF) + "." +
                    (destinationAddress[3] & 0xFF);
        } else if (addressType.equals(AddressType.DOMAINNAME)) {
            return new String(destinationAddress, 0, destinationAddressSize);
        } else {
            throw new Socks5Exception("Address type not supported",
                    ReplyCode.SOCKS5.ADDRESS_TYPE_NOT_SUPPORTED);
        }
    }
}