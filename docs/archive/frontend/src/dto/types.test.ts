import {describe, expect, it} from 'vitest';
import type {StartupCandidate, StudioChartResponse} from '@/dto/types';

describe('studio dto contracts', () => {
  it('accepts startup candidate payload shape', () => {
    const candidate: StartupCandidate = {
      symbol: 'SBIN',
      rank: 1,
      masterScore: 1.23,
      rsScore: 0.5,
      volumeExpansionScore: 1.1,
      trendEfficiencyScore: 0.2,
      openingDriveScore: 0.05,
      closePaisa: 78000,
      barTimeMs: 1716537900000,
    };
    expect(candidate.symbol).toBe('SBIN');
  });

  it('accepts studio chart payload shape', () => {
    const payload: StudioChartResponse = {
      symbol: 'SBIN',
      exchangeSegment: 'NSE_EQ',
      interval: '5m',
      from: '2026-05-01',
      to: '2026-05-01',
      count: 1,
      candles: [{
        startTimeMs: 1716537900000,
        endTimeMs: 1716538199999,
        openPaisa: 78000,
        highPaisa: 78100,
        lowPaisa: 77900,
        closePaisa: 78050,
        volume: 1000,
      }],
      halfTrend: [{value: 780.0, direction: 'up', high: 781.0, low: 779.0}],
      cvd: [{cvd: 1000, volumeDelta: 1000}],
      markers: [{type: 'swing_high', timeMs: 1716537900000, price: 781.0}],
      orderBlockZones: [{
        startTimeMs: 1716537900000,
        endTimeMs: 1716538199999,
        top: 781.0,
        bottom: 779.0,
        bias: 'bullish',
      }],
    };
    expect(payload.count).toBe(1);
    expect(payload.halfTrend).toHaveLength(1);
  });
});
