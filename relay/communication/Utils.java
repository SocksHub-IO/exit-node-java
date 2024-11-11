package relay.communication;

import java.io.IOException;
import java.io.InputStream;

public final class Utils {

    private Utils() {
        throw new IllegalStateException("This is a utility class that cannot be instantiated!");
    }

    // To be used only for reading from the relay
    public static boolean readExactly(InputStream inputStream, byte[] buffer, int size) {
        int bytesReceived = 0;

        do {
            try {
                bytesReceived += inputStream.read(buffer, bytesReceived, size - bytesReceived);

                if(bytesReceived == -1) {
                    return false;
                }
            } catch (IOException ioe) {
                return false;
            }
        } while (bytesReceived < size);

        return true;
    }

    public static long extractRemoteId(byte[] idBytes) {
        if (idBytes.length != 8) {
            throw new IllegalArgumentException("Byte array must be exactly 8 bytes long.");
        }

        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (idBytes[i] & 0xFF);
        }
        return value;
    }
}
