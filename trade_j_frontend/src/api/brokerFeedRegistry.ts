import type { BrokerFeedClient } from "./brokerFeedClient";
import { DhanBrokerFeedClient } from "./dhanBrokerFeedClient";
import { UpstoxBrokerFeedClient, IciciBrokerFeedClient } from "./upstoxIciciBrokerFeedClient";

export interface BrokerSession {
  broker: string;
  accessToken: string;
  clientId?: string;
  sessionToken?: string;
  instrumentKeys?: string[];
}

const registry: Record<string, (session: BrokerSession) => BrokerFeedClient> = {
  DHAN: (s) => new DhanBrokerFeedClient({
    accessToken: s.accessToken,
    clientId: s.clientId ?? "",
    mode: "FULL",
  }),
  UPSTOX: (s) => new UpstoxBrokerFeedClient({ accessToken: s.accessToken }, s.instrumentKeys ?? []),
  ICICI: (s) => new IciciBrokerFeedClient({ sessionToken: s.sessionToken ?? s.accessToken }),
};

export function brokerFeedClient(session: BrokerSession): BrokerFeedClient {
  const factory = registry[session.broker];
  if (!factory) throw new Error(`Unknown broker: ${session.broker}`);
  return factory(session);
}

export function listSupportedBrokers(): string[] {
  return Object.keys(registry);
}
