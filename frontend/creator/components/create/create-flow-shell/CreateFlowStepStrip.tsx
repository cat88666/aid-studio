'use client'

/**
 * 顶部横向流程条（原 CreateFlowShell.vue create-workflow 段拆分）。
 */

import { LoadingOutlined } from '@ant-design/icons'
import type { CreationStep } from '~/types'
import type { CreationFlowStepMeta } from '~/utils/createFlowStepMeta'
import { useCreateCopy } from '../useCreateCopy'

const stepTitles: Record<CreationStep, string> = {
  'global-setting': 'Setup', 'story-script': 'Script', 'scene-character': 'Assets',
  'storyboard-script': 'Storyboard', 'storyboard-video': 'Video', dubbing: 'Audio', preview: 'Export'
}

export interface CreateFlowStepStripProps {
  displaySteps: CreationFlowStepMeta[]
  flowStepIndex: number
  stepStatus: Array<'completed' | 'pending' | 'disabled' | 'active'>
  unlockedStepIndex: number
  stepRealIndex: (stepKey: CreationStep) => number
  isConnectorTrailDone: (index: number) => boolean
  isPillDisabled: (index: number) => boolean
  /** 从案例复制/预览进入时，高亮全部流程入口。 */
  highlightAll?: boolean
  /** 素材准备（scene-character）步 loading（提取中 / 第三步可视生成中） */
  sceneCharacterGenerating: boolean
  storyboardScriptGenerating: boolean
  storyboardVideoGenerating: boolean
  dubbingGenerating: boolean
  onPillClick: (index: number) => void
  onPillIntent: (step: CreationStep) => void
}

export function CreateFlowStepStrip({
  displaySteps,
  flowStepIndex,
  stepStatus,
  unlockedStepIndex,
  stepRealIndex,
  isPillDisabled,
  highlightAll = false,
  sceneCharacterGenerating,
  storyboardScriptGenerating,
  storyboardVideoGenerating,
  dubbingGenerating,
  onPillClick,
  onPillIntent
}: CreateFlowStepStripProps) {
  const copy = useCreateCopy()
  function pillLoading(stepKey: CreationStep): boolean {
    if (stepKey === 'scene-character') return sceneCharacterGenerating
    if (stepKey === 'storyboard-script') return storyboardScriptGenerating
    if (stepKey === 'storyboard-video') return storyboardVideoGenerating
    if (stepKey === 'dubbing') return dubbingGenerating
    return false
  }

  return (
    <nav className="create-workflow" aria-label={copy('创作步骤', 'Creation steps')}>
      <div className="create-workflow__scroll">
        {displaySteps.map((step, displayIndex) => {
          const realIndex = stepRealIndex(step.key)
          const isActive = flowStepIndex === realIndex
          const pillClasses = [
            'flow-step-pill',
            'flow-step-pill--strip',
            isActive ? 'flow-step-pill--active' : '',
            stepStatus[realIndex] === 'completed' ? 'flow-step-pill--completed' : '',
            stepStatus[realIndex] === 'pending' ? 'flow-step-pill--pending' : '',
            stepStatus[realIndex] === 'disabled' ? 'flow-step-pill--disabled' : '',
            realIndex > unlockedStepIndex ? 'flow-step-pill--locked-ahead' : '',
            highlightAll ? 'flow-step-pill--entry-breathing' : ''
          ]
            .filter(Boolean)
            .join(' ')
          return (
            <div key={step.key} className="flow-step-pill-wrap">
                <button
                  type="button"
                  className={pillClasses}
                  aria-current={isActive ? 'step' : undefined}
                  disabled={isPillDisabled(realIndex)}
                  onPointerEnter={() => onPillIntent(step.key)}
                  onPointerDown={() => onPillIntent(step.key)}
                  onFocus={() => onPillIntent(step.key)}
                  onClick={() => onPillClick(realIndex)}
                >
                  <span className="flow-step-pill__icon" aria-hidden="true">
                    {pillLoading(step.key) ? (
                      <LoadingOutlined className="flow-step-pill__loading" spin />
                    ) : (
                      <span className="flow-step-number">{String(displayIndex + 1).padStart(2, '0')}</span>
                    )}
                  </span>
                  <span className="flow-step-pill__title">{copy(step.title, stepTitles[step.key])}</span>
                </button>
              </div>

          )
        })}
      </div>
    </nav>
  )
}
