import { describe, expect, it } from 'vitest'
import { buildStoryboardModalGenerateRequest } from './storyboardModalGenerateRequest'

describe('分镜图片右侧生成', () => {
  it('没有上传素材时按纯提示词提交，不附加参考媒体字段', () => {
    expect(buildStoryboardModalGenerateRequest({
      storyboardIds: [123],
      imagePrompt: '清晨街道上的行人',
      modelName: 'image-model',
      aspectRatio: '16:9',
      count: 1
    })).toEqual({
      storyboardIds: [123],
      imagePrompt: '清晨街道上的行人',
      modelName: 'image-model',
      aspectRatio: '16:9',
      count: 1
    })
  })
})
