package com.tradej.gateway.protocol;

import java.nio.charset.StandardCharsets;

/**
 * Binary wire protocol codec for the gateway.
 * Encodes/decodes gateway frames with a simple header: topic byte + 8-byte sequence + payload.
 */
public final class GatewayBinaryCodec {

    private static final int HEADER_SIZE = 9; // 1 byte topic + 8 bytes sequence

    private GatewayBinaryCodec() {
    }

    /**
     * Encode a topic, sequence number, and payload into a binary frame.
     */
    public static byte[] encode(GatewayTopic topic, long sequence, byte[] payload) {
        byte[] frame = new byte[HEADER_SIZE + payload.length];
        frame[0] = (byte) topic.wireId();
        frame[1] = (byte) (sequence >>> 56);
        frame[2] = (byte) (sequence >>> 48);
        frame[3] = (byte) (sequence >>> 40);
        frame[4] = (byte) (sequence >>> 32);
        frame[5] = (byte) (sequence >>> 24);
        frame[6] = (byte) (sequence >>> 16);
        frame[7] = (byte) (sequence >>> 8);
        frame[8] = (byte) sequence;
        System.arraycopy(payload, 0, frame, HEADER_SIZE, payload.length);
        return frame;
    }

    /**
     * Decode a binary frame into a {@link GatewayFrame}.
     */
    public static GatewayFrame decode(byte[] frame) {
        if (frame.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Frame too short: " + frame.length);
        }
        GatewayTopic topic = GatewayTopic.fromWireId(frame[0] & 0xFF);
        long sequence = ((long) frame[1] & 0xFF) << 56
                | ((long) frame[2] & 0xFF) << 48
                | ((long) frame[3] & 0xFF) << 40
                | ((long) frame[4] & 0xFF) << 32
                | ((long) frame[5] & 0xFF) << 24
                | ((long) frame[6] & 0xFF) << 16
                | ((long) frame[7] & 0xFF) << 8
                | ((long) frame[8] & 0xFF);
        byte[] payload = new byte[frame.length - HEADER_SIZE];
        System.arraycopy(frame, HEADER_SIZE, payload, 0, payload.length);
        return new GatewayFrame(topic, sequence, payload);
    }

    /**
     * Check if a byte array is a gateway control frame (has header).
     */
    public static boolean isGatewayFrame(byte[] data) {
        if (data.length < HEADER_SIZE) {
            return false;
        }
        int wireId = data[0] & 0xFF;
        for (GatewayTopic topic : GatewayTopic.values()) {
            if (topic.wireId() == wireId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decode raw bytes as a UTF-8 string.
     */
    public static String decodeUtf8(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Encode a string as UTF-8 bytes.
     */
    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A decoded gateway frame with topic, sequence, and payload.
     */
    public record GatewayFrame(GatewayTopic topic, long sequence, byte[] payload) {
    }
}
