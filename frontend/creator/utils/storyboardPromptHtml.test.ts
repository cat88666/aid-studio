// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import {
  plainPromptTextToEditorHtml,
  storyboardPromptHtmlToPlain,
  storyboardPromptPlainToHtml
} from './storyboardPromptHtml'

describe('storyboardPromptHtmlToPlain', () => {
  it('preserves line breaks and spaces from prompt editors', () => {
    expect(
      storyboardPromptHtmlToPlain('<p>镜头缓慢推进  保留双空格</p><p>角色转身<br>停顿一秒</p>')
    ).toBe('镜头缓慢推进  保留双空格\n角色转身\n停顿一秒')
  })

  it('renders API prompt whitespace without parsing text as markup', () => {
    const plain = '第一行  连续空格\n\t\t第二行 <保持原文>'
    const html = plainPromptTextToEditorHtml(plain)

    expect(html).toBe('<p>第一行  连续空格<br/>\t\t第二行 &lt;保持原文&gt;</p>')
    expect(storyboardPromptHtmlToPlain(html)).toBe(plain)
  })

  it('keeps line breaks around prompt reference segments', () => {
    const html = storyboardPromptPlainToHtml('第一行\n第二行', [], [])

    expect(html).toBe('<p>第一行<br/>第二行</p>')
    expect(storyboardPromptHtmlToPlain(html)).toBe('第一行\n第二行')
  })
})
