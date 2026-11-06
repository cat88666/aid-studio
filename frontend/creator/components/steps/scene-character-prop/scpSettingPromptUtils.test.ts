import { describe, expect, it } from 'vitest'

import type { UserAssetRpsFormRow } from '~/types/business-api'

import {
  buildRpsSettingPromptUpdateRequest,
  isRpsSettingPromptEditable,
  settingEditorStateFromRpsForm
} from './scpSettingPromptUtils'

function form(overrides: Partial<UserAssetRpsFormRow>): UserAssetRpsFormRow {
  return {
    id: 1,
    name: '默认形态',
    ...overrides
  }
}

describe('scpSettingPromptUtils', () => {
  it('keeps every character form bound to its own descriptions field', () => {
    const first = settingEditorStateFromRpsForm(
      form({ id: 11, descriptions: '红色礼服', createSource: 'auto' }),
      'character'
    )
    const second = settingEditorStateFromRpsForm(
      form({ id: 12, descriptions: '黑色战甲', createSource: 'auto' }),
      'character'
    )

    expect(first).toMatchObject({ formId: 11, content: '<p>红色礼服</p>' })
    expect(second).toMatchObject({ formId: 12, content: '<p>黑色战甲</p>' })
  })

  it('preserves API prompt whitespace when opening the setting editor', () => {
    const state = settingEditorStateFromRpsForm(
      form({ id: 13, descriptions: '正面  站立\n\t侧面转身', createSource: 'auto' }),
      'character'
    )

    expect(state.content).toBe('<p>正面  站立<br/>\t侧面转身</p>')
  })

  it('uses descriptions for character forms and prompt for prop forms', () => {
    expect(buildRpsSettingPromptUpdateRequest('character', 21, '<p>青年<br/>短发</p>')).toEqual(
      { id: 21, descriptions: '青年\n短发' }
    )
    expect(buildRpsSettingPromptUpdateRequest('prop', 31, '<p>青铜长剑</p>')).toEqual({
      id: 31,
      prompt: '青铜长剑'
    })
  })

  it('allows both manual and automatic forms to edit their prompts', () => {
    expect(
      isRpsSettingPromptEditable(
        settingEditorStateFromRpsForm(form({ id: 41, prompt: '旧木箱', createSource: 'manual' }), 'prop')
      )
    ).toBe(true)
    expect(
      isRpsSettingPromptEditable(
        settingEditorStateFromRpsForm(form({ id: 42, prompt: '金属箱', createSource: 'auto' }), 'prop')
      )
    ).toBe(true)
  })
})
