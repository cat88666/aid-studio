import { CREATOR_BRAND_NAME } from '~/config/creatorBrand'
import './PublicBrandLogo.css'

interface PublicBrandLogoProps {
  className?: string
  alt?: string
}

export default function PublicBrandLogo({ className, alt }: PublicBrandLogoProps) {
  return (
    <span className={['public-brand-logo', className].filter(Boolean).join(' ')} aria-label={alt || CREATOR_BRAND_NAME} role="img">
      <span className="public-brand-logo__text" aria-hidden="true">{CREATOR_BRAND_NAME}</span>
    </span>
  )
}
