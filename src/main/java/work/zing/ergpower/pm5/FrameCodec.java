package work.zing.ergpower.pm5;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses a bridge/replay NDJSON frame line into a {@link Pm5Frame}. Shared by
 * {@code ReplayPm5Source} and {@code BleP m5Source} so live and replayed captures use the exact same
 * wire format: {@code {"hostTime": ISO-8601, "mono": seconds, "uuid": full-uuid, "bytes": hex}}.
 */
public final class FrameCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FrameCodec() {
    }

    public static Pm5Frame parse(String jsonLine) throws IOException {
        JsonNode n = MAPPER.readTree(jsonLine);
        Instant hostTime = Instant.parse(n.get("hostTime").asText());
        double mono = n.has("mono") ? n.get("mono").asDouble() : 0.0;
        int id = Pm5Frame.shortIdFromUuid(n.get("uuid").asText());
        return new Pm5Frame(hostTime, mono, id, hexToBytes(n.get("bytes").asText()));
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex, 2 * i, 2 * i + 2, 16);
        }
        return out;
    }
}
