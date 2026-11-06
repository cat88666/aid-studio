/** 移动端专属提示页路径（与 proxy.ts / RouteGuard 共用） */
export const MOBILE_ONLY_PATH = '/mobile'

export function isMobileUserAgent(ua: string): boolean {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Mobile|Windows Phone/i.test(
    ua
  )
}

/**
 * 是否应对该路径执行移动端拦截。
 * 公开首页支持移动设备，工作台仍使用专属提示页。
 */
export function shouldForceMobileOnlyPath(pathname: string): boolean {
  return pathname !== "/" && pathname.replace(/\/$/, "") !== MOBILE_ONLY_PATH
}

/** 桌面端访问 `/mobile` 时应回首页 */
export function shouldLeaveMobileOnlyPath(pathname: string, isMobile: boolean): boolean {
  return !isMobile && pathname === MOBILE_ONLY_PATH
}
