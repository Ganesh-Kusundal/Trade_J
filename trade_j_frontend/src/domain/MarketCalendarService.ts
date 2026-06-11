import type { Instrument } from "./instrument";
import { MarketState } from "./instrument";

export class MarketCalendarService {
  static getMarketState(exchange: string, instrument: Instrument, now = new Date()): MarketState {
    const ist = this.toIST(now);
    if (!this.isWeekday(ist)) return MarketState.CLOSED;
    if (this.isHoliday(exchange, ist)) return MarketState.HOLIDAY;

    const hhmm = ist.getHours() * 100 + ist.getMinutes();

    switch (exchange.toUpperCase()) {
      case "NSE":
      case "BSE":
        if (hhmm >= 900 && hhmm < 915) return MarketState.PREOPEN;
        if (hhmm >= 915 && hhmm < 1530) return MarketState.OPEN;
        if (hhmm >= 1530 && hhmm < 1600) return MarketState.AUCTION;
        return MarketState.CLOSED;

      case "NFO":
        if (hhmm >= 915 && hhmm < 1530) return MarketState.OPEN;
        return MarketState.CLOSED;

      case "CDS":
        if (hhmm >= 900 && hhmm < 1700) return MarketState.OPEN;
        return MarketState.CLOSED;

      case "MCX": {
        const isDST = this.isUSDST(ist);
        const mcxClose = isDST ? 2330 : 2355;
        if (hhmm >= 900 && hhmm < mcxClose) return MarketState.OPEN;
        return MarketState.CLOSED;
      }

      default:
        return MarketState.UNKNOWN;
    }
  }

  static toIST(date: Date): Date {
    return new Date(date.toLocaleString("en-US", { timeZone: "Asia/Kolkata" }));
  }

  static isWeekday(ist: Date): boolean {
    const day = ist.getDay();
    return day !== 0 && day !== 6;
  }

  static isUSDST(date: Date): boolean {
    const m = date.getMonth() + 1;
    if (m > 3 && m < 11) return true;
    if (m === 3) return date.getDate() >= this.getNthSunday(date.getFullYear(), 3, 2);
    if (m === 11) return date.getDate() < this.getNthSunday(date.getFullYear(), 11, 1);
    return false;
  }

  static getNthSunday(year: number, month: number, n: number): number {
    const d = new Date(year, month - 1, 1);
    const firstSunday = (7 - d.getDay()) % 7 + 1;
    return firstSunday + (n - 1) * 7;
  }

  static isHoliday(exchange: string, ist: Date): boolean {
    return false;
  }

  static getNextTransition(exchange: string, state: MarketState): string {
    switch (state) {
      case MarketState.CLOSED: return "PREOPEN tomorrow 09:00 IST";
      case MarketState.PREOPEN: return "OPEN at 09:15 IST";
      case MarketState.OPEN:
        switch (exchange.toUpperCase()) {
          case "MCX": return "CLOSE at 23:30 IST";
          case "CDS": return "CLOSE at 17:00 IST";
          default: return "CLOSE at 15:30 IST";
        }
      case MarketState.AUCTION: return "CLOSED at 16:00 IST";
      default: return "";
    }
  }
}
