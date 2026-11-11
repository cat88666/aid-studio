import type { AuthPublicConfigData } from '~/types/business-api'
import { CREATOR_BRAND_NAME, CREATOR_BRAND_DESCRIPTION, CREATOR_BRAND_KEYWORDS, CREATOR_BRAND_ICON } from './creatorBrand'
import { withAppBasePath } from '~/utils/appBasePath'

export const CREATOR_POLICY_LINKS = {
  terms: withAppBasePath('/policies/terms/'),
  privacy: withAppBasePath('/policies/privacy/')
}

/** Creator owns its public identity and policy links; shared admin settings remain unchanged. */
export function creatorPublicConfig<T extends AuthPublicConfigData>(data: T): T {
  return {
    ...data,
    basic: {
      work_publish_enabled: data.basic?.work_publish_enabled,
      site_name: CREATOR_BRAND_NAME,
      site_description: CREATOR_BRAND_DESCRIPTION,
      site_keywords: CREATOR_BRAND_KEYWORDS,
      terms_of_service: CREATOR_POLICY_LINKS.terms,
      privacy_policy: CREATOR_POLICY_LINKS.privacy,
      membership_agreement: withAppBasePath('/policies/terms/#credits'),
      app_permissions_description: withAppBasePath('/policies/permissions/'),
      personal_information_collection_list: withAppBasePath('/policies/data/'),
      third_party_sdk_and_information_sharing_list: withAppBasePath('/policies/providers/'),
      tutorial_url: withAppBasePath('/guide/')
    },
    brand: { platformLogoUrl: '', faviconUrl: CREATOR_BRAND_ICON }
  }
}
