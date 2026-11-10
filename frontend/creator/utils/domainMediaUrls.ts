/** Keep legacy server media references on the current public Creator origin. */
export function normalizeDomainMediaUrls<T>(value: T): T {
  if (typeof value === 'string') return value.replace(/^(?:https?:\/\/43\.154\.247\.11(?::\d+)?)?\/profile\//, 'https://thdrhdf.site/profile/') as T
  if (Array.isArray(value)) return value.map(normalizeDomainMediaUrls) as T
  if (value && Object.getPrototypeOf(value) === Object.prototype) {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, normalizeDomainMediaUrls(item)])) as T
  }
  return value
}
