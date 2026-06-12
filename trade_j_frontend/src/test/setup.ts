import '@testing-library/jest-dom/vitest';

if (!globalThis.matchMedia) {
  globalThis.matchMedia = (q: string) => ({
    matches: false,
    media: q,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}

if (!('ResizeObserver' in globalThis)) {
  (globalThis as unknown as {ResizeObserver: unknown}).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

if (!('IntersectionObserver' in globalThis)) {
  (globalThis as unknown as {IntersectionObserver: unknown}).IntersectionObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
  };
}
