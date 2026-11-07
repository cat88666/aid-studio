// Node 26 exposes Web Storage on the global object before Vitest installs the
// browser environment. Bind to the actual jsdom window rather than Node's
// optional process-wide --localstorage-file storage.
const browserWindow = (globalThis as unknown as { jsdom?: { window: Window } }).jsdom?.window
if (browserWindow) {
  for (const key of ['localStorage', 'sessionStorage'] as const) {
    Object.defineProperty(globalThis, key, {
      configurable: true,
      writable: true,
      value: browserWindow[key]
    })
  }
}
