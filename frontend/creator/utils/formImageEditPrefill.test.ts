import { describe, expect, it } from 'vitest'
import {
  resolveFormImageEditPrefill,
  shouldApplyFormImageDialoguePrefill
} from './formImageEditPrefill'

describe('shouldApplyFormImageDialoguePrefill', () => {
  it('does not overwrite the composer after generate focuses a new empty image', () => {
    expect(
      shouldApplyFormImageDialoguePrefill({
        modalOpen: true,
        preserveComposer: true,
        selectionChanged: true,
        modalJustOpened: false,
        currentInstructionPlain: '把画面改成夕阳氛围',
        incomingPromptText: ''
      })
    ).toBe(false)
  })

  it('does not wipe a typed prompt when list refresh replaces the current image', () => {
    expect(
      shouldApplyFormImageDialoguePrefill({
        modalOpen: true,
        preserveComposer: false,
        selectionChanged: false,
        modalJustOpened: false,
        currentInstructionPlain: '把画面改成夕阳氛围',
        incomingPromptText: ''
      })
    ).toBe(false)
  })

  it('prefills when the user switches to another image', () => {
    expect(
      shouldApplyFormImageDialoguePrefill({
        modalOpen: true,
        preserveComposer: false,
        selectionChanged: true,
        modalJustOpened: false,
        currentInstructionPlain: '把画面改成夕阳氛围',
        incomingPromptText: '角色站在窗边'
      })
    ).toBe(true)
  })

  it('fills an empty composer when saved prompt arrives later', () => {
    expect(
      shouldApplyFormImageDialoguePrefill({
        modalOpen: true,
        preserveComposer: false,
        selectionChanged: false,
        modalJustOpened: false,
        currentInstructionPlain: '',
        incomingPromptText: '角色站在窗边'
      })
    ).toBe(true)
  })

  it('prefills when the modal is reopened', () => {
    expect(
      shouldApplyFormImageDialoguePrefill({
        modalOpen: true,
        preserveComposer: false,
        selectionChanged: false,
        modalJustOpened: true,
        currentInstructionPlain: '上次留下的文案',
        incomingPromptText: '角色站在窗边'
      })
    ).toBe(true)
  })

  it('does not prefill when the modal is closed', () => {
    expect(
      shouldApplyFormImageDialoguePrefill({
        modalOpen: false,
        preserveComposer: false,
        selectionChanged: true,
        modalJustOpened: false,
        currentInstructionPlain: '',
        incomingPromptText: '角色站在窗边'
      })
    ).toBe(false)
  })
})

describe('resolveFormImageEditPrefill', () => {
  it('treats a newly generated image without promptText as empty composer input', () => {
    expect(resolveFormImageEditPrefill({ promptText: '', referenceImages: [] })).toEqual({
      promptText: '',
      sourceImages: []
    })
  })
})
