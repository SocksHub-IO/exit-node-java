package relay;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import relay.exception.RelayIOException;

public final class Utils {

    // To be used only for reading from the relay
    public static boolean readExactly(InputStream inputStream, byte[] buffer, int size) throws RelayIOException {
        int total = 0;

        do {
            try {
                int received = inputStream.read(buffer, total, size - total);

                if(received == -1) {
                    return false;
                } else {
                    total += received;
                }
            } catch (IOException ioe) {
                return false;
            }
        } while (total < size);

        return true;
    }

    public static String extractRemoteID(byte[] idBytes) {
        return extractIPAddress(idBytes) + extractPort(idBytes);
    }

    // Extracts the IP address from the 4 bytes format
    private static String extractIPAddress(byte[] idBytes) {
        byte[] ipBytes = new byte[4];
        System.arraycopy(idBytes, 0, ipBytes, 0, 4);
        try {
            InetAddress inetAddress = InetAddress.getByAddress(ipBytes);
            return inetAddress.getHostAddress();
        } catch (UnknownHostException uhe){
            // ignore. this id format is temporary
            return "0.0.0.0";
        }
    }

    // Extracts the port number from the 2 bytes format
    private static int extractPort(byte[] idBytes) {
        return ((idBytes[4] & 0xFF) << 8) | (idBytes[5] & 0xFF); // Big-endian
    }
}
