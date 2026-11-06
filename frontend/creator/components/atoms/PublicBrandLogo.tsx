'use client'

import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { creatorBrandName } from '~/config/creatorBrand'
import './PublicBrandLogo.css'

interface PublicBrandLogoProps {
  className?: string
  alt?: string
}

export default function PublicBrandLogo({ className, alt }: PublicBrandLogoProps) {
  const { locale } = useCreatorLocale()
  return (
    <span className={['public-brand-logo', className].filter(Boolean).join(' ')} aria-label={alt || creatorBrandName(locale)} role="img">
      <span className="public-brand-logo__text" aria-hidden="true">{creatorBrandName(locale)}</span>
    </span>
  )
}
