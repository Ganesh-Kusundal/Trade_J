import { useState } from "react";
import { runScan, type ScanHit } from "../api/scanner";

export function useScanner(defaultProfile = "default") {
  const [hits, setHits] = useState<ScanHit[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [profile, setProfile] = useState(defaultProfile);
  const [lastRun, setLastRun] = useState<Date | null>(null);

  const handleRunScan = async () => {
    try {
      setLoading(true);
      setError("");

      const result = await runScan(profile);
      setHits(result.hits);
      setLastRun(new Date(result.run.finishedAtMs));
    } catch (err: any) {
      setError(err.message || "Scan failed");
    } finally {
      setLoading(false);
    }
  };

  return { hits, loading, error, profile, setProfile, runScan: handleRunScan, lastRun };
}
