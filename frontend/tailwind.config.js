/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{js,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        terminalBg: '#070709',
        terminalPanel: '#09090b',
        terminalBorder: '#1c1c1e',
        terminalBorder2: '#18181b',
        terminalText: '#e4e4e7',
        terminalMuted: '#71717a',
        terminalAccent: '#00d2ff',
      },
      boxShadow: {
        terminal: '0 10px 40px rgba(0,0,0,0.55)',
      },
    },
  },
  plugins: [],
};
