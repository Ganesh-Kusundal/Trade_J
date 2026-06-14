import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import LiveTerminal from './LiveTerminal.tsx';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LiveTerminal />
  </StrictMode>,
);
