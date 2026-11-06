'use client'

import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { CREATOR_BRAND_NAME } from '~/config/creatorBrand'
import { getAidPortalRoot } from '~/utils/portalRoot'

/** 复用跨流程导航的视觉，避免页面 transform 限制全屏遮罩。 */
export function PageLoadingOverlay({ label = '页面加载中…' }: { label?: string }) {
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  if (!mounted) return null

  const portalRoot = getAidPortalRoot()
  if (!portalRoot) return null

  return createPortal(
    <div className="route-overlay" role="status" aria-live="polite" aria-label={label}>
      <div className="route-overlay__inner">
        <div className="route-overlay__glow" />
        <div className="route-overlay__brand">{CREATOR_BRAND_NAME}</div>
        <div className="route-overlay__hint">{label}</div>
        <div className="route-overlay__bar" aria-hidden="true"><i /></div>
      </div>
    </div>,
    portalRoot
  )
}
