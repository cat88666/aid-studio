import { beforeEach, describe, expect, it } from 'vitest'

import {
  peekCreateFlowStepModalIntent,
  requestCreateFlowStepModal,
  requestStoryboardImageStepModal,
  resetCreateFlowStepModalIntentForTest
} from './createFlowStepModalIntent'

describe('requestStoryboardImageStepModal', () => {
  beforeEach(() => {
    resetCreateFlowStepModalIntentForTest()
  })

  it.each(['pro', 'multi'])('无分镜图模式 %s 不创建弹窗意图并清除旧状态', (creationMode) => {
    requestCreateFlowStepModal('storyboard-image', 1)

    expect(requestStoryboardImageStepModal(creationMode, 2)).toBeNull()
    expect(peekCreateFlowStepModalIntent()).toBeNull()
  })

  it.each(['i2v', 'auto_grid'])('需要分镜图的模式 %s 保留原有开窗行为', (creationMode) => {
    expect(requestStoryboardImageStepModal(creationMode, 2)).toMatchObject({
      kind: 'storyboard-image',
      panelIndex: 2
    })
    expect(peekCreateFlowStepModalIntent()).toMatchObject({
      kind: 'storyboard-image',
      panelIndex: 2
    })
  })
})
