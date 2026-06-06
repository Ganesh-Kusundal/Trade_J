import {useState, useRef, useEffect} from 'react';
import {Bot, Send, X, Terminal, Cpu} from 'lucide-react';

interface Message {
  sender: 'user' | 'agent';
  text: string;
  toolCall?: {
    name: string;
    result: string;
  };
}

export function AgentChat() {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([
    {
      sender: 'agent',
      text: 'Hello! I am your Antigravity trading co-pilot. I can query our DuckDB database, fetch scanner hits, run backtest sessions, and evaluate market regimes using my 16 read-only MCP tools. How can I help you today?',
    },
  ]);
  const [loading, setLoading] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({behavior: 'smooth'});
  }, [messages, loading]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || loading) return;

    const userText = input;
    setInput('');
    setMessages((prev) => [...prev, {sender: 'user', text: userText}]);
    setLoading(true);

    // Simulate an intelligent agent routing through the MCP tools
    setTimeout(async () => {
      let replyText = '';
      let toolCallData = undefined;

      try {
        const lower = userText.toLowerCase();
        if (lower.includes('scanner') || lower.includes('hit')) {
          // Simulate tool call: get_scanner_hits
          const res = await fetch('/api/research/analytics/run-results');
          const data = res.ok ? await res.json() : [];
          toolCallData = {
            name: 'get_scanner_hits',
            result: JSON.stringify(data.slice(0, 3), null, 2),
          };
          replyText = `I have executed the read-only tool 'get_scanner_hits'. Here are the latest scanner run records from the DuckDB research database:`;
        } else if (lower.includes('result') || lower.includes('backtest') || lower.includes('performance')) {
          // Simulate tool call: get_performance_metrics
          const res = await fetch('/api/research/analytics/run-results');
          const data = res.ok ? await res.json() : [];
          toolCallData = {
            name: 'get_performance_metrics',
            result: JSON.stringify(data[0] || {message: "No backtest runs found"}, null, 2),
          };
          replyText = `Exposing execution quality from the latest backtest run via the 'get_performance_metrics' tool:`;
        } else {
          replyText = `I can see you're interested in analyzing the platform. Try asking me to 'check scanner hits' or 'show backtest results' to see me invoke my MCP JSON-RPC tools against the DuckDB schemas!`;
        }
      } catch (err) {
        replyText = `Failed to process agent routing: ${err}`;
      }

      setMessages((prev) => [
        ...prev,
        {
          sender: 'agent',
          text: replyText,
          toolCall: toolCallData,
        },
      ]);
      setLoading(false);
    }, 1200);
  };

  return (
    <>
      {/* Floating Action Button */}
      <button
        onClick={() => setIsOpen(true)}
        className="fixed bottom-24 right-6 z-40 bg-[#00d2ff] hover:bg-[#00b2d6] text-[#070709] w-12 h-12 rounded-full shadow-2xl flex items-center justify-center cursor-pointer transition-transform hover:scale-105"
        title="Open Agent Co-pilot"
      >
        <Bot size={22} fill="#070709" />
      </button>

      {/* Slide-out Drawer */}
      <div
        className={`fixed top-12 right-0 bottom-0 w-[400px] z-50 bg-[#09090b]/95 border-l border-zinc-800/80 backdrop-blur-md shadow-2xl flex flex-col transition-transform duration-300 ${
          isOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        {/* Header */}
        <div className="h-14 border-b border-zinc-850 px-4 flex items-center justify-between bg-zinc-950/60">
          <div className="flex items-center gap-2">
            <Cpu size={16} className="text-[#00d2ff]" />
            <span className="text-xs font-black tracking-widest text-zinc-100 uppercase font-mono">
              Co-Pilot Terminal
            </span>
          </div>
          <button
            onClick={() => setIsOpen(false)}
            className="text-zinc-500 hover:text-zinc-300 cursor-pointer"
          >
            <X size={16} />
          </button>
        </div>

        {/* Chat Messages */}
        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
          {messages.map((msg, idx) => (
            <div
              key={idx}
              className={`flex flex-col gap-1 max-w-[85%] ${
                msg.sender === 'user' ? 'self-end items-end' : 'self-start items-start'
              }`}
            >
              <div
                className={`px-3.5 py-2.5 rounded-lg text-xs leading-relaxed font-mono ${
                  msg.sender === 'user'
                    ? 'bg-[#00d2ff] text-[#070709] font-bold rounded-tr-none'
                    : 'bg-zinc-900 border border-zinc-800 text-zinc-300 rounded-tl-none'
                }`}
              >
                {msg.text}
              </div>

              {msg.toolCall && (
                <div className="mt-1.5 w-[320px] bg-zinc-950/80 border border-zinc-800 rounded-md overflow-hidden self-start">
                  <div className="bg-zinc-900/60 px-3 py-1.5 border-b border-zinc-800 flex items-center gap-1.5 justify-between">
                    <div className="flex items-center gap-1.5">
                      <Terminal size={10} className="text-[#10b981]" />
                      <span className="text-[8.5px] font-black font-mono text-[#10b981] uppercase tracking-wide">
                        {msg.toolCall.name}
                      </span>
                    </div>
                    <span className="text-[8px] font-mono text-zinc-600 font-bold">READ ONLY</span>
                  </div>
                  <pre className="p-3 text-[9px] font-mono text-zinc-400 overflow-x-auto whitespace-pre leading-normal max-h-[150px]">
                    {msg.toolCall.result}
                  </pre>
                </div>
              )}
            </div>
          ))}

          {loading && (
            <div className="self-start flex items-center gap-1 px-3.5 py-2.5 rounded-lg bg-zinc-900 border border-zinc-800 rounded-tl-none">
              <span className="w-1.5 h-1.5 bg-zinc-500 rounded-full animate-bounce" />
              <span className="w-1.5 h-1.5 bg-zinc-500 rounded-full animate-bounce delay-100" />
              <span className="w-1.5 h-1.5 bg-zinc-500 rounded-full animate-bounce delay-200" />
            </div>
          )}
          <div ref={chatEndRef} />
        </div>

        {/* Form Input */}
        <form onSubmit={handleSend} className="p-4 border-t border-zinc-850 bg-zinc-950/40 flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask co-pilot to scan or run backtests..."
            className="flex-1 bg-[#0e0e11] border border-zinc-800 focus:border-zinc-700 h-9 px-3 rounded-md text-xs font-mono text-zinc-200 outline-none placeholder-zinc-650"
          />
          <button
            type="submit"
            className="bg-[#00d2ff] hover:bg-[#00b2d6] text-[#070709] w-9 h-9 rounded-md flex items-center justify-center cursor-pointer transition-colors"
          >
            <Send size={14} />
          </button>
        </form>
      </div>
    </>
  );
}
