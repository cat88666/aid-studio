'use client'

import { message } from 'antd'
import { useRouter } from 'next/navigation'
import { beginEnterCreateFlowOverlay, endEnterCreateFlowOverlay } from './useEnterCreateFlowOverlay'
import { useCreationStore } from '~/stores/creation'
import type { UserProjectType } from '~/types/business-api'
import { buildOpenProjectFlowQuery, resolveCreateFlowEntryPath } from '~/utils/createFlowProjectContext'
import { hydrateCreationStoreFromProjectDetail, resetProjectDetailHydrateCache } from '~/utils/hydrateCreationStoreFromProjectDetail'

export function useOpenLibraryProject() {
  const router = useRouter()
  return async (id: number, initialType: UserProjectType) => {
    if (!Number.isSafeInteger(id) || id <= 0) {
      message.error('项目ID无效')
      return
    }
    async function entryHref(projectType: UserProjectType) {
      const entry = await resolveCreateFlowEntryPath(id, projectType)
      const query = buildOpenProjectFlowQuery(id, { embedded: false, projectType })
      return entry + '?' + new URLSearchParams(query).toString()
    }
    beginEnterCreateFlowOverlay()
    try {
      resetProjectDetailHydrateCache(id)
      router.push(await entryHref(initialType))
      await hydrateCreationStoreFromProjectDetail(useCreationStore.getState(), id, { force: true })
      const type = useCreationStore.getState().currentProjectType
      if (type === 'series') useCreationStore.getState().setSeriesFlowEnteredStoryScript(false)
      if (type && type !== initialType) router.replace(await entryHref(type))
    } catch {
      endEnterCreateFlowOverlay()
      message.error('获取项目详情失败，请稍后重试')
    }
  }
}
