import { decodeUpstoxFrame } from "../api/upstoxBinaryDecoder";

let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.log(`  FAIL: ${name}`); }
}

function buildFrameWithLtp(instrumentKey: string, ltp: number, ltt: number): ArrayBuffer {
  // LTPC { ltp: double wire=1, ltt: int64 wire=0 }
  const ltpcBytes: number[] = [];
  ltpcBytes.push(0x09); // field 1, wire 1 (64-bit)
  const ltpBuf = new ArrayBuffer(8);
  new DataView(ltpBuf).setFloat64(0, ltp, true);
  new Uint8Array(ltpBuf).forEach((b) => ltpcBytes.push(b));
  ltpcBytes.push(0x10); // field 2, wire 0 (varint)
  let v = ltt;
  while (v > 0x7f) { ltpcBytes.push((v & 0x7f) | 0x80); v >>>= 7; }
  ltpcBytes.push(v & 0x7f);

  // Feed { ltpc: LTPC }
  const feedBytes: number[] = [];
  feedBytes.push(0x0a); // field 1, wire 2 (length-delimited)
  let len = ltpcBytes.length;
  while (len > 0x7f) { feedBytes.push((len & 0x7f) | 0x80); len >>>= 7; }
  feedBytes.push(len);
  ltpcBytes.forEach((b) => feedBytes.push(b));

  // Map entry { key: string, value: Feed }
  const keyBytes = new TextEncoder().encode(instrumentKey);
  const entryBytes: number[] = [];
  entryBytes.push(0x0a);
  let klen = keyBytes.length;
  while (klen > 0x7f) { entryBytes.push((klen & 0x7f) | 0x80); klen >>>= 7; }
  entryBytes.push(klen);
  keyBytes.forEach((b) => entryBytes.push(b));
  entryBytes.push(0x12);
  let flen = feedBytes.length;
  while (flen > 0x7f) { entryBytes.push((flen & 0x7f) | 0x80); flen >>>= 7; }
  entryBytes.push(flen);
  feedBytes.forEach((b) => entryBytes.push(b));

  // FeedResponse { type: 1, feeds: [entry] }
  const respBytes: number[] = [];
  respBytes.push(0x08, 0x01);
  respBytes.push(0x12);
  let rlen = entryBytes.length;
  while (rlen > 0x7f) { respBytes.push((rlen & 0x7f) | 0x80); rlen >>>= 7; }
  respBytes.push(rlen);
  entryBytes.forEach((b) => respBytes.push(b));

  const payload = new Uint8Array(respBytes);
  const framed = new Uint8Array(2 + payload.length);
  framed[0] = (payload.length >> 8) & 0xff;
  framed[1] = payload.length & 0xff;
  framed.set(payload, 2);
  return framed.buffer;
}

console.log("UpstoxBinaryDecoder tests");

assert((() => {
  const ticks = decodeUpstoxFrame(buildFrameWithLtp("NSE_EQ|NIFTY", 2500.5, 1700000000));
  if (ticks.length !== 1) {
    console.log("  DBG: got", ticks.length, "ticks");
    return false;
  }
  if (ticks[0].symbol !== "NSE_EQ|NIFTY") {
    console.log("  DBG: symbol mismatch:", ticks[0].symbol);
    return false;
  }
  if (Math.abs(ticks[0].ltp - 2500.5) > 0.001) {
    console.log("  DBG: ltp mismatch:", ticks[0].ltp, "expected ~2500.5");
    return false;
  }
  if (ticks[0].ts !== 1700000000) {
    console.log("  DBG: ts mismatch:", ticks[0].ts, "expected 1700000000");
    return false;
  }
  if (ticks[0].origin !== "BROKER_LIVE") {
    console.log("  DBG: origin mismatch:", ticks[0].origin);
    return false;
  }
  return true;
})(), "extracts LTP from a single-instrument frame");

assert((() => {
  const ticks = decodeUpstoxFrame(new ArrayBuffer(0));
  return ticks.length === 0;
})(), "returns empty for an empty buffer");

assert((() => {
  const garbage = new Uint8Array([0xff, 0xff, 0xff, 0xff, 0x12, 0x34]).buffer;
  let threw = false;
  try { decodeUpstoxFrame(garbage); } catch (e) { threw = true; }
  return !threw;
})(), "skips a malformed frame without throwing");

assert((() => {
  const f1 = buildFrameWithLtp("NSE_EQ|NIFTY", 2500.0, 1000);
  const f2 = buildFrameWithLtp("NSE_EQ|RELIANCE", 2900.0, 2000);
  const combined = new Uint8Array(f1.byteLength + f2.byteLength);
  combined.set(new Uint8Array(f1), 0);
  combined.set(new Uint8Array(f2), f1.byteLength);
  const ticks = decodeUpstoxFrame(combined.buffer);
  if (ticks.length !== 2) return false;
  const byKey = new Map(ticks.map((t) => [t.symbol, t.ltp]));
  return Math.abs(byKey.get("NSE_EQ|NIFTY") - 2500.0) < 0.001
      && Math.abs(byKey.get("NSE_EQ|RELIANCE") - 2900.0) < 0.001;
})(), "decodes multiple frames in one buffer");

console.log(`\n  UpstoxBinaryDecoder: ${passed} passed, ${failed} failed`);
if (failed > 0) process.exit(1);
