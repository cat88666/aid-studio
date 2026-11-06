export const LOGIN_SUCCESS_MIN_LOADING_MS = 1000

export async function waitForLoginMinimumLoading(
  startedAt: number,
  now: () => number = Date.now
): Promise<void> {
  const remaining = LOGIN_SUCCESS_MIN_LOADING_MS - (now() - startedAt)
  if (remaining <= 0) return
  await new Promise<void>((resolve) => globalThis.setTimeout(resolve, remaining))
}
