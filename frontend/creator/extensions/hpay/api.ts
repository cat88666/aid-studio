import { request } from '~/utils/api'
import type { ApiEnvelope } from '~/types/business-api'

export interface HPayPack { id: number; name: string; credits: number; amount: number; currency: 'USDT' }
export interface HPayOrder {
  orderNo: string; productName: string; credits: number; amount: number; currency: 'USDT'
  status: 'pending' | 'paid'; checkoutUrl: string | null; createdAt: string
}
export interface HPayCatalog { enabled: boolean; currency: 'USDT'; packages: HPayPack[] }
async function call<T>(path: string, body: object = {}): Promise<T> {
  const result = await request.post<ApiEnvelope<T>>(`/api/user/extensions/hpay/${path}`, body)
  return result.data as T
}
export const hpay = {
  catalog: () => call<HPayCatalog>('catalog'),
  orders: () => call<HPayOrder[]>('orders'),
  create: (packageId: number) => call<HPayOrder>('create', { packageId }),
  query: (orderNo: string) => call<HPayOrder>('query', { orderNo })
}
export function safeCheckoutUrl(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null
  } catch { return null }
}
