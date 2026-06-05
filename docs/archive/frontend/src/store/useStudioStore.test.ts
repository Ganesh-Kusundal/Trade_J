import {describe, expect, it} from 'vitest';

function subtractCalendarDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

describe('studio bootstrap date range', () => {
  it('computes twenty day lookback ending on scan date', () => {
    expect(subtractCalendarDays('2026-05-29', 19)).toBe('2026-05-10');
  });
});

describe('startup candidates response shape', () => {
  it('includes scan cutoff metadata', () => {
    const response = {
      scanDate: '2026-05-29',
      scanTime: '14:45:00',
      requestedScanTime: '09:45:00',
      chartLookbackDays: 20,
      selectionMode: 'baseline',
      provenance: {requestedScanTime: '09:45:00', fallbackReason: 'used_first_bar_after_cutoff'},
      candidates: [{symbol: 'SBIN', rank: 1, masterScore: 1, barTimeMs: 1}],
    };
    expect(response.requestedScanTime).toBe('09:45:00');
    expect(response.chartLookbackDays).toBe(20);
    expect(response.provenance.fallbackReason).toBe('used_first_bar_after_cutoff');
  });
});
