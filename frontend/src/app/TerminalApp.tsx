import {TerminalLayout} from '@/ui/layout/TerminalLayout';
import {useGatewaySocket} from '@/hooks/useGatewaySocket';

export function TerminalApp() {
  useGatewaySocket();
  return <TerminalLayout />;
}
