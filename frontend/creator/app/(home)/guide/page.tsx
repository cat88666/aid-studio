'use client'

import Link from 'next/link'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import { creatorBrandName } from '~/config/creatorBrand'
import '../creator-home.css'

export default function CreatorGuidePage() {
  const { locale } = useCreatorLocale()
  const chinese = locale === 'zh' || locale === 'zh-hant'
  const t = (zh: string, en: string) => chinese ? zh : en
  const steps = [
    [t('选择创作方式', 'Choose how to create'), t('打开创作台，使用 AI 对话整理选题、文案和剧本；需要分镜等完整流程时，进入项目创作。', 'Open the studio to brainstorm topics, copy and scripts with AI Chat. Use project creation for the storyboard workflow.')],
    [t('选择模型并开始对话', 'Choose a model and start chatting'), t('在 AI 对话的模型选择器中选择模型，再输入需求并发送。免费模型是可选项，原有默认模型仍然保留。', 'Choose a model in AI Chat before sending your prompt. Free models are optional; the existing default remains available.')],
    [t('创建或继续项目', 'Create or resume a project'), t('从项目列表打开已有作品，或新建项目并填写名称、作品类型、画面比例和模型配置，然后编写或导入剧本。', 'Open an existing work from the project list, or create a project with a name, type, aspect ratio and model settings. Then write or import your script.')],
    [t('按步骤制作与检查', 'Create and review step by step'), t('在项目中依次整理角色、场景、道具和分镜。生成前检查所选模型、参数及费用提示；完成后预览结果，再继续下一步。', 'Organize characters, scenes, props and storyboards in the project. Check the selected model, settings and cost notice before generating. Review each result before continuing.')]
  ]

  return (
    <div className="creator-landing" lang={chinese ? 'zh' : 'en'}>
      <main className="creator-container creator-content">
        <section className="creator-section" aria-labelledby="guide-title">
          <p className="creator-label">{creatorBrandName(locale)}</p>
          <h1 id="guide-title">{t('创作指南', 'Creator guide')}</h1>
          <p className="creator-lead">{t('从第一段文案到项目创作，在这里了解创作台的使用方法。', 'Learn how to use the studio, from your first prompt to a creative project.')}</p>
          <div className="creator-hero-actions">
            <Link href="/start/">{t('进入创作台', 'Open studio')}</Link>
            <Link href="/works/">{t('查看我的项目', 'View my projects')}</Link>
          </div>
        </section>
        <section className="creator-section" aria-label={t('操作步骤', 'Getting started')}>
          <div className="creator-stages">
            {steps.map(([title, description], index) => (
              <article key={title}><small>0{index + 1}</small><h2>{title}</h2><p>{description}</p></article>
            ))}
          </div>
        </section>
        <section className="creator-section" aria-labelledby="guide-help">
          <h2 id="guide-help">{t('使用说明', 'Usage notes')}</h2>
          <div className="creator-faq">
            <details open>
              <summary>{t('免费模型与额度', 'Free models and limits')}</summary>
              <p>{t('免费模型受上游额度、限流和可用性影响，不保证长期无限使用。若提示额度耗尽或请求失败，可稍后重试或切换其他模型；切换前请确认费用提示。', 'Free models have provider quotas, rate limits and availability constraints. Unlimited ongoing use is not guaranteed. If a request fails or the quota is exhausted, retry later or choose another model after checking its cost.')}</p>
            </details>
            <details open>
              <summary>{t('图片、视频与视频复刻', 'Images, video and video remix')}</summary>
              <p>{t('创作台独立图片、视频和视频复刻入口目前显示“尚未接入”时，暂不能直接提交生成。模型可选择不代表该入口已开放生成；请以页面状态为准。', 'Standalone image, video and video remix generation cannot be submitted while the studio shows “Not connected”. A model selector does not mean generation is available; follow the status shown on the page.')}</p>
            </details>
            <details>
              <summary>{t('项目与资产在哪里？', 'Where are projects and assets?')}</summary>
              <p>{t('登录后通过“项目”查看已有作品，通过“资产库”管理素材。请使用创建作品时的同一账号；如果没有项目，可新建项目开始创作。', 'Sign in to view existing works under Projects and manage materials in Assets. Use the same account that created the work. If the list is empty, create a new project to begin.')}</p>
            </details>
          </div>
        </section>
      </main>
    </div>
  )
}
