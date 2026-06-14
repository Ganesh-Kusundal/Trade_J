export const DataModes = {
  LIVE: "LIVE",
  SIMULATION: "SIMULATION",
  HISTORICAL: "HISTORICAL",
  PAPER: "PAPER",
  REPLAY: "REPLAY",
} as const;

export type DataMode = (typeof DataModes)[keyof typeof DataModes];

export function resolveDataMode(
  feedHealthy: boolean,
  marketOpen: boolean,
  hasCredentials: boolean
): DataMode {
  if (feedHealthy && marketOpen) return DataModes.LIVE;
  if (feedHealthy && !marketOpen) return DataModes.HISTORICAL;
  if (!feedHealthy && marketOpen) return DataModes.SIMULATION;
  return DataModes.HISTORICAL;
}
