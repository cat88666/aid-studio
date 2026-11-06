import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

function readWorkspaceFile(path: string): string {
  return readFileSync(new URL(`../${path}`, import.meta.url), 'utf-8')
}

describe('confirm modal icon/title layout', () => {
  const css = readWorkspaceFile('assets/css/app-confirm-modal.css')

  it('keeps icon and title on one row for Ant Design 6 paragraph structure', () => {
    expect(css).toContain('ant-modal-confirm-paragraph')
    expect(css).toMatch(/ant-modal-confirm-body[\s\S]{0,400}flex-wrap:\s*nowrap\s*!important/)
    expect(css).not.toMatch(/ant-modal-confirm-body[\s\S]{0,400}flex-wrap:\s*wrap\s*!important/)
  })

  it('does not treat title as a direct sibling of the custom icon', () => {
    expect(css).not.toContain('.app-confirm-icon + .ant-modal-confirm-title')
  })
})
