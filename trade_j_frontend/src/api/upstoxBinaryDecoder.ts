import type { FeedTick, FeedDepth } from "./brokerFeedClient";

/**
 * Minimal Upstox v3 MarketDataFeed wire decoder.
 *
 * <p>Upstox v3 sends binary frames where each frame is a
 * {@code FeedResponse} protobuf message. The protobuf encoding is
 * tag-length-value:
 *
 * <ul>
 *   <li>tag byte = (field_number << 3) | wire_type</li>
 *   <li>wire_type 0 = varint, 1 = 64-bit, 2 = length-delimited,
 *       5 = 32-bit</li>
 *   <li>varints are LEB128 (7 bits per byte, MSB = continuation)</li>
 * </ul>
 *
 * <p>The Upstox v3 SDK v2 upgrade added a 2-byte big-endian length
 * prefix on each frame. We strip that first, then walk the
 * {@code FeedResponse} message enough to find the
 * {@code feeds: map<string, Feed>} entries and, inside each
 * {@code Feed}, the {@code LTPC} oneof (field 1, length-delimited).
 *
 * <p>This is a deliberately small implementation. It extracts the
 * LTP for every key in the feeds map. Full depth, greeks, and
 * option-chain parsing are deferred to a follow-up that uses a
 * generated TS descriptor.
 */
export class UpstoxBinaryDecoder {
  decode(buffer: ArrayBuffer): FeedTick[] {
    const out: FeedTick[] = [];
    const bytes = new Uint8Array(buffer);
    let cursor = 0;
    while (cursor < bytes.length) {
      if (bytes.length - cursor < 2) break;
      // 2-byte big-endian length prefix
      const payloadLength = (bytes[cursor] << 8) | bytes[cursor + 1];
      cursor += 2;
      if (bytes.length - cursor < payloadLength) break;
      const payloadEnd = cursor + payloadLength;
      try {
        this.walkFeedResponse(bytes, cursor, payloadEnd, out);
      } catch (e) {
        // Malformed frame; skip rather than crash the WS.
      }
      cursor = payloadEnd;
    }
    return out;
  }

  private walkFeedResponse(
    bytes: Uint8Array, start: number, end: number, out: FeedTick[]
  ): void {
    let p = start;
    while (p < end) {
      const tag = bytes[p++];
      const field = tag >>> 3;
      const wire = tag & 0x07;
      // Field 2 is the feeds map (length-delimited entries).
      if (field === 2 && wire === 2) {
        const entryLen = this.readVarint(bytes, p); p = this.afterVarint(bytes, p);
        const entryEnd = Math.min(p + entryLen, end);
        this.walkFeedMapEntry(bytes, p, entryEnd, out);
        p = entryEnd;
      } else {
        p = this.skipField(bytes, p - 1, end);
      }
    }
  }

  private walkFeedMapEntry(
    bytes: Uint8Array, start: number, end: number, out: FeedTick[]
  ): void {
    let p = start;
    let instrumentKey: string | null = null;
    let ltp: number | null = null;
    let ltt: number | null = null;
    let ltq: number | null = null;
    while (p < end) {
      const tag = bytes[p++];
      const field = tag >>> 3;
      const wire = tag & 0x07;
      if (field === 1 && wire === 2) {
        // Map key (string)
        const len = this.readVarint(bytes, p); p = this.afterVarint(bytes, p);
        instrumentKey = this.readString(bytes, p, len); p += len;
      } else if (field === 2 && wire === 2) {
        // Feed value (length-delimited)
        const len = this.readVarint(bytes, p); p = this.afterVarint(bytes, p);
        const valueEnd = Math.min(p + len, end);
        const { ltp: vLtp, ltt: vLtt, ltq: vLtq } = this.walkFeed(bytes, p, valueEnd);
        if (vLtp != null) ltp = vLtp;
        if (vLtt != null) ltt = vLtt;
        if (vLtq != null) ltq = vLtq;
        p = valueEnd;
      } else {
        p = this.skipField(bytes, p - 1, end);
      }
    }
    if (instrumentKey != null && ltp != null) {
      out.push({
        symbol: instrumentKey,
        exchangeSegment: "NSE_EQ",
        ltp,
        ts: ltt != null ? Number(ltt) : Date.now(),
        origin: "BROKER_LIVE",
      });
      if (ltq != null) {
        // quantity not in FeedTick shape; ignored for now
      }
    }
  }

  /**
   * Walks a Feed message looking for the LTPC oneof (field 1,
   * length-delimited). Returns the LTP, ltt, and ltq.
   */
  private walkFeed(
    bytes: Uint8Array, start: number, end: number
  ): { ltp: number | null; ltt: number | null; ltq: number | null } {
    let p = start;
    let ltp: number | null = null;
    let ltt: number | null = null;
    let ltq: number | null = null;
    while (p < end) {
      const tag = bytes[p++];
      const field = tag >>> 3;
      const wire = tag & 0x07;
      if (field === 1 && wire === 2) {
        // LTPC
        const len = this.readVarint(bytes, p); p = this.afterVarint(bytes, p);
        const ltpcEnd = Math.min(p + len, end);
        const parsed = this.walkLtpc(bytes, p, ltpcEnd);
        ltp = parsed.ltp;
        ltt = parsed.ltt;
        ltq = parsed.ltq;
        p = ltpcEnd;
      } else {
        p = this.skipField(bytes, p - 1, end);
      }
    }
    return { ltp, ltt, ltq };
  }

  private walkLtpc(
    bytes: Uint8Array, start: number, end: number
  ): { ltp: number | null; ltt: number | null; ltq: number | null } {
    let p = start;
    let ltp: number | null = null;
    let ltt: number | null = null;
    let ltq: number | null = null;
    while (p < end) {
      const tag = bytes[p++];
      const field = tag >>> 3;
      const wire = tag & 0x07;
      // LTPC.ltp = 1 (double, wire 1 = 64-bit)
      // LTPC.ltt = 2 (int64, wire 0 = varint)
      // LTPC.ltq = 3 (int64, wire 0 = varint)
      // LTPC.cp  = 4 (double, wire 1)
      if (field === 1 && wire === 1) {
        ltp = this.readDouble(bytes, p); p += 8;
      } else if ((field === 2 || field === 3) && wire === 0) {
        const v = this.readVarint(bytes, p); p = this.afterVarint(bytes, p);
        if (field === 2) ltt = v; else ltq = v;
      } else {
        p = this.skipField(bytes, p - 1, end);
      }
    }
    return { ltp, ltt, ltq };
  }

  // ── Wire-format primitives ─────────────────────────────────────────

  private readVarint(bytes: Uint8Array, p: number): number {
    let result = 0;
    let shift = 0;
    let pos = p;
    while (pos < bytes.length) {
      const b = bytes[pos++];
      result |= (b & 0x7f) << shift;
      if ((b & 0x80) === 0) return result;
      shift += 7;
      if (shift > 63) throw new Error("varint too long");
    }
    throw new Error("truncated varint");
  }

  private afterVarint(bytes: Uint8Array, p: number): number {
    while (p < bytes.length) {
      if ((bytes[p++] & 0x80) === 0) return p;
    }
    throw new Error("truncated varint");
  }

  private readString(bytes: Uint8Array, p: number, length: number): string {
    let s = "";
    for (let i = 0; i < length; i++) {
      s += String.fromCharCode(bytes[p + i]);
    }
    return s;
  }

  private readDouble(bytes: Uint8Array, p: number): number {
    // 64-bit little-endian IEEE 754 double
    const view = new DataView(bytes.buffer, bytes.byteOffset + p, 8);
    return view.getFloat64(0, true);
  }

  private skipField(bytes: Uint8Array, tagPos: number, end: number): number {
    const tag = bytes[tagPos];
    const wire = tag & 0x07;
    let p = tagPos + 1;
    switch (wire) {
      case 0: return this.afterVarint(bytes, p);
      case 1: return p + 8;
      case 2: {
        const len = this.readVarint(bytes, p); p = this.afterVarint(bytes, p);
        return p + len;
      }
      case 5: return p + 4;
      default: return end;
    }
  }
}

export function decodeUpstoxFrame(buffer: ArrayBuffer): FeedTick[] {
  return new UpstoxBinaryDecoder().decode(buffer);
}
