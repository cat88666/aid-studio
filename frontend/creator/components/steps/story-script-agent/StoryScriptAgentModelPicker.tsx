'use client'

import { CheckOutlined, DownOutlined, RobotOutlined } from '@ant-design/icons'
import { Popover, Tooltip } from 'antd'
import { useEffect, useState } from 'react'
import type { UserSkillModelDefinition } from '~/types/user-skill'

interface StoryScriptAgentModelPickerProps {
  models: UserSkillModelDefinition[]
  selectedModelCode: string
  disabled?: boolean
  onSelect: (modelCode: string) => void
}

export function StoryScriptAgentModelPicker({
  models,
  selectedModelCode,
  disabled = false,
  onSelect
}: StoryScriptAgentModelPickerProps) {
  const [open, setOpen] = useState(false)
  const availableModels = models.filter((model) => String(model.modelCode || '').trim())
  const selectedModel = availableModels.find((model) => model.modelCode === selectedModelCode)
  const selectedLabel = selectedModel?.modelName || selectedModel?.modelCode || selectedModelCode || '选择模型'

  useEffect(() => {
    if (!open) return undefined
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      event.stopPropagation()
      setOpen(false)
    }
    window.addEventListener('keydown', onKeyDown, true)
    return () => window.removeEventListener('keydown', onKeyDown, true)
  }, [open])

  const trigger = (
    <button
      type="button"
      className={`story-agent-model-picker__trigger${open ? ' is-open' : ''}`}
      aria-label={`模型：${selectedLabel}`}
      aria-haspopup="listbox"
      aria-expanded={open}
      disabled={disabled || availableModels.length === 0}
    >
      <RobotOutlined aria-hidden />
      <span>{selectedLabel}</span>
      <DownOutlined className="story-agent-model-picker__arrow" aria-hidden />
    </button>
  )

  return (
    <Popover
      trigger="click"
      placement="topLeft"
      arrow={false}
      open={disabled ? false : open}
      onOpenChange={(next) => {
        if (!disabled) setOpen(next)
      }}
      destroyOnHidden
      getPopupContainer={() => document.body}
      classNames={{ root: 'story-agent-model-picker__popover' }}
      styles={{
        container: { padding: 0, background: 'transparent' },
        content: { padding: 0 }
      }}
      content={
        <div className="story-agent-model-picker__panel" role="listbox" aria-label="选择模型">
          <header>模型</header>
          <div className="story-agent-model-picker__list">
            {availableModels.map((model) => {
              const active = model.modelCode === selectedModelCode
              const logoUrl = model.modelLogo || model.providerLogo
              return (
                <button
                  key={model.modelCode}
                  type="button"
                  role="option"
                  aria-selected={active}
                  className={`story-agent-model-picker__item${active ? ' is-active' : ''}`}
                  onClick={() => {
                    onSelect(model.modelCode)
                    setOpen(false)
                  }}
                >
                  <span className="story-agent-model-picker__logo">
                    {/* Catalog logos may be external tenant resources and are not compatible with Next image optimization. */}
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    {logoUrl ? <img src={logoUrl} alt="" /> : <RobotOutlined aria-hidden />}
                  </span>
                  <span className="story-agent-model-picker__meta">
                    <strong>{model.modelName || model.modelCode}</strong>
                    <small>{model.providerName || model.modelCode}</small>
                  </span>
                  {active ? <CheckOutlined className="story-agent-model-picker__check" aria-hidden /> : null}
                </button>
              )
            })}
          </div>
        </div>
      }
    >
      <Tooltip title={open ? undefined : `模型：${selectedLabel}`} placement="top">
        {trigger}
      </Tooltip>
    </Popover>
  )
}

export default StoryScriptAgentModelPicker
