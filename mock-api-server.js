#!/usr/bin/env node
// ============================================================================
// Mock Backend API Server for Trade-J Frontend Testing
// Simulates backend REST API endpoints
// ============================================================================

const http = require('http');
const url = require('url');

const PORT = 8080;

// Mock candle data generator
function generateCandles(symbol, timeframe, limit = 500) {
  const candles = [];
  const now = Math.floor(Date.now() / 1000);
  
  // Parse timeframe
  const match = timeframe.match(/^(\d+)([mhd])$/);
  const intervalSeconds = match ? 
    match[2] === 'm' ? parseInt(match[1]) * 60 :
    match[2] === 'h' ? parseInt(match[1]) * 3600 :
    parseInt(match[1]) * 86400 : 300;
  
  // Base price per symbol
  const basePrices = {
    'NIFTY': 22000,
    'BANKNIFTY': 48000,
    'RELIANCE': 2500,
    'TCS': 3800,
    'INFY': 1500,
    'HDFCBANK': 1600,
    'ICICIBANK': 1000
  };
  
  let basePrice = basePrices[symbol] || 2000;
  
  for (let i = limit; i >= 0; i--) {
    const timestamp = now - (i * intervalSeconds);
    const volatility = basePrice * 0.002;
    const change = (Math.random() - 0.5) * volatility;
    
    const open = basePrice + (Math.random() - 0.5) * volatility;
    const close = open + change;
    const high = Math.max(open, close) + Math.random() * volatility * 0.5;
    const low = Math.min(open, close) - Math.random() * volatility * 0.5;
    const volume = Math.floor(1000 + Math.random() * 5000);
    
    candles.push({
      symbol,
      exchangeSegment: symbol.includes('NIFTY') || symbol.includes('BANKNIFTY') ? 'IDX_I' : 'NSE_EQ',
      timestamp,
      open: parseFloat(open.toFixed(2)),
      high: parseFloat(high.toFixed(2)),
      low: parseFloat(low.toFixed(2)),
      close: parseFloat(close.toFixed(2)),
      volume,
      vwap: parseFloat(((high + low + close) / 3).toFixed(2))
    });
    
    basePrice = close;
  }
  
  return candles;
}

// Request handler
const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const query = parsedUrl.query;
  
  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Accept');
  res.setHeader('Content-Type', 'application/json');
  
  // Handle preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }
  
  console.log(`${new Date().toISOString()} ${req.method} ${pathname}`);
  
  // Health endpoint
  if (pathname === '/actuator/health') {
    res.writeHead(200);
    res.end(JSON.stringify({
      status: 'UP',
      components: {
        db: { status: 'UP' },
        broker: { status: 'UP' }
      }
    }));
    return;
  }
  
  // Market candles endpoint
  if (pathname === '/api/v1/market/candles') {
    const symbol = query.symbol || 'NIFTY';
    const timeframe = query.timeframe || '5m';
    const limit = parseInt(query.limit) || 500;
    const broker = query.broker || 'dhan';
    
    console.log(`  → Generating ${limit} candles for ${symbol} @ ${timeframe} (${broker})`);
    
    const candles = generateCandles(symbol, timeframe, limit);
    
    res.writeHead(200);
    res.end(JSON.stringify({
      symbol,
      timeframe,
      broker,
      count: candles.length,
      candles
    }));
    return;
  }
  
  // Replay candles endpoint
  if (pathname === '/api/v1/replay/candles') {
    const symbol = query.symbol || 'NIFTY';
    const timeframe = query.timeframe || '5m';
    const start = parseInt(query.start);
    const end = parseInt(query.end);
    
    console.log(`  → Generating replay candles for ${symbol} @ ${timeframe}`);
    
    // Generate candles in the time range
    const candles = generateCandles(symbol, timeframe, 500);
    const filtered = candles.filter(c => c.timestamp >= start && c.timestamp <= end);
    
    res.writeHead(200);
    res.end(JSON.stringify({
      symbol,
      timeframe,
      startTime: start,
      endTime: end,
      count: filtered.length,
      candles: filtered
    }));
    return;
  }
  
  // 404
  res.writeHead(404);
  res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, () => {
  console.log('═══════════════════════════════════════════════════════');
  console.log(`  Trade-J Mock API Server`);
  console.log(`  Running on http://localhost:${PORT}`);
  console.log(`  ${new Date().toISOString()}`);
  console.log('═══════════════════════════════════════════════════════');
  console.log('');
  console.log('Endpoints:');
  console.log('  GET /actuator/health');
  console.log('  GET /api/v1/market/candles?symbol=NIFTY&timeframe=5m&broker=dhan');
  console.log('  GET /api/v1/replay/candles?symbol=NIFTY&timeframe=5m&start=X&end=Y');
  console.log('');
  console.log('Press Ctrl+C to stop');
  console.log('');
});
