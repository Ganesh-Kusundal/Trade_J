export interface BrokerCredentialField {
  key: string;
  label: string;
  type: string;
  placeholder: string;
}

export interface BrokerInfo {
  source: string;
  displayName: string;
  supportedSegments: string[];
  capabilities: Record<string, boolean>;
  credentialFields: BrokerCredentialField[];
  version: string;
}

export async function fetchBrokers(): Promise<BrokerInfo[]> {
  try {
    const res = await fetch("/api/v1/brokers");
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}
