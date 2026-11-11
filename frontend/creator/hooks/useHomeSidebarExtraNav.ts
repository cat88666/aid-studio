'use client'

import { useRouter } from 'next/navigation'

/** 创作台与创作流程共用站内指南，不依赖历史后台教程链接。 */
export function useHomeSidebarExtraNav() {
  const router = useRouter()

  function openTutorial() {
    router.push('/guide/')
  }

  return { openTutorial }
}
