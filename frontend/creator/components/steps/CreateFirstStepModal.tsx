'use client'

import { useCreateCopy } from '~/components/create/useCreateCopy'

import { LoadingOutlined } from '@ant-design/icons'
import { Modal } from 'antd'
import CreateFirstStepFormBody from '~/components/steps/CreateFirstStepFormBody'
import type { GlobalSettingData } from '~/types'
import './create-first-step-modal.css'
type RatioValue = '16:9' | '9:16' | '4:3' | '3:4' | '1:1' | '21:9'
type ProjectTypeValue = 'movie' | 'series'
type ScriptTypeValue = GlobalSettingData['scriptType']
type StrategyValue = GlobalSettingData['modelStrategy']
type CreationModeValue = GlobalSettingData['creationMode']

export interface CreateFirstStepModalProps {
  open: boolean
  title: string
  projectType: ProjectTypeValue
  aspectRatio: RatioValue
  scriptType: ScriptTypeValue
  modelStrategy: StrategyValue
  creationMode: CreationModeValue
  modelValue: GlobalSettingData
  confirmLoading?: boolean
  flowEditMode?: boolean
  projectTypeLocked?: boolean
  contentConfigLocked?: boolean
  contentConfigLockReason?: 'storyboard' | 'assets'
  syncProjectTypeFromParent?: boolean
  onOpenChange: (open: boolean) => void
  onTitleChange: (value: string) => void
  onProjectTypeChange: (value: ProjectTypeValue) => void
  onAspectRatioChange: (value: RatioValue) => void
  onScriptTypeChange: (value: ScriptTypeValue) => void
  onModelStrategyChange: (value: StrategyValue) => void
  onCreationModeChange: (value: CreationModeValue) => void
  onModelValueChange: (value: GlobalSettingData) => void
  onConfirm: () => void
}

/** 创建作品第一步弹窗（新建项目 / 项目配置），原 CreateFirstStepModal.vue */
export function CreateFirstStepModal({
  open,
  title,
  projectType,
  aspectRatio,
  scriptType,
  modelStrategy,
  creationMode,
  modelValue,
  confirmLoading = false,
  flowEditMode = false,
  projectTypeLocked = false,
  contentConfigLocked = false,
  contentConfigLockReason,
  syncProjectTypeFromParent = false,
  onOpenChange,
  onTitleChange,
  onProjectTypeChange,
  onAspectRatioChange,
  onScriptTypeChange,
  onModelStrategyChange,
  onCreationModeChange,
  onModelValueChange,
  onConfirm
}: CreateFirstStepModalProps) {
  const copy = useCreateCopy()
  const confirmButtonLabel = flowEditMode ? copy('保存', 'Save') : copy('创建作品', 'Create project')
  const confirmLoadingLabel = flowEditMode ? copy('保存中…', 'Saving…') : copy('创建中…', 'Creating…')
  const modalTitleText = flowEditMode ? copy('项目配置', 'Project settings') : copy('新建项目', 'New project')

  function onConfirmClick() {
    if (confirmLoading) return
    onConfirm()
  }

  return (
    <Modal
      open={open}
      footer={null}
      centered
      title={modalTitleText}
      closable={!confirmLoading}
      keyboard={!confirmLoading}
      destroyOnHidden
      width={960}
      wrapClassName="creator-project-dialog"
      className="create-first-step-modal"
      onCancel={() => !confirmLoading && onOpenChange(false)}
    >
      <div className="modal-shell">
        <CreateFirstStepFormBody
          open={open}
          flowEditMode={flowEditMode}
          projectTypeLocked={projectTypeLocked}
          contentConfigLocked={contentConfigLocked}
          contentConfigLockReason={contentConfigLockReason}
          syncProjectTypeFromParent={syncProjectTypeFromParent}
          title={title}
          projectType={projectType}
          aspectRatio={aspectRatio}
          scriptType={scriptType}
          modelStrategy={modelStrategy}
          creationMode={creationMode}
          modelValue={modelValue}
          className="modal-body"
          onTitleChange={onTitleChange}
          onProjectTypeChange={onProjectTypeChange}
          onAspectRatioChange={onAspectRatioChange}
          onScriptTypeChange={onScriptTypeChange}
          onModelStrategyChange={onModelStrategyChange}
          onCreationModeChange={onCreationModeChange}
          onModelValueChange={onModelValueChange}
        />

        <div className="modal-actions">
          <button
            type="button"
            className="btn btn-secondary"
            disabled={confirmLoading}
            onClick={() => !confirmLoading && onOpenChange(false)}
          >
            {copy('取消', 'Cancel')}
          </button>
          <button
            type="button"
            className="btn btn-primary"
            disabled={confirmLoading}
            onClick={onConfirmClick}
          >
            {confirmLoading && <LoadingOutlined spin className="btn-loading-icon" />}
            <span>{confirmLoading ? confirmLoadingLabel : confirmButtonLabel}</span>
          </button>
        </div>
      </div>
    </Modal>
  )
}

export default CreateFirstStepModal
