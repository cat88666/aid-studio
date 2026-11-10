'use client'

import { CREATOR_BRAND_NAME } from '~/config/creatorBrand'

/** Compatibility entry point for the former animated logo. */
export default function SakuraHoverLogo({
  alt = CREATOR_BRAND_NAME,
  className
}: {
  alt?: string
  className?: string
}) {
  return <span className={className} aria-label={alt}>{CREATOR_BRAND_NAME}</span>
}
