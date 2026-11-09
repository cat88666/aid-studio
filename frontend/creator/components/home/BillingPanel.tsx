'use client'

import { useEffect, useMemo, useState } from 'react'
import { Select } from 'antd'
import { DownOutlined, SearchOutlined } from '@ant-design/icons'
import { publicBillingDetail } from '~/utils/businessApi'
import { deriveBillingScenarios, formatRuleExample } from '~/utils/billingDisplay'
import {
  filterBillingModels,
  listBillingModelProviders,
  type BillingModelTypeFilter
} from '~/utils/billingModelFilter'
import type {
  BillingColumnVO,
  BillingRuleItemVO,
  ModelBillingDetailVO,
  PublicBillingDetailData
} from '~/types/business-api'
import { useCreatorLocale } from '~/hooks/useCreatorLocale'
import pricingText from '~/components/billing/pricingText.json'
import './BillingPanel.css'

type BillingTab = BillingModelTypeFilter

const tabs = [
  { label: '全部', value: 'all' as BillingTab },
  { label: '文本', value: 'text' as BillingTab },
  { label: '图片', value: 'image' as BillingTab },
  { label: '视频', value: 'video' as BillingTab },
  { label: '配音', value: 'audio' as BillingTab }
]

function formatBillingCell(rule: BillingRuleItemVO, col: BillingColumnVO) {
  const value = (rule as Record<string, unknown>)[col.key]
  if (value == null || value === '') return '—'
  if (col.type === 'number') {
    const num = Number(value)
    return Number.isFinite(num) ? String(num) : String(value)
  }
  return String(value)
}

function hasInputPricing(model: ModelBillingDetailVO) {
  const p = model.inputPricing
  if (!p) return false
  return Boolean(p.imageSupported || p.videoSupported)
}

export default function BillingPanel() {
  const { locale } = useCreatorLocale()
  const chinese = locale === 'zh' || locale === 'zh-hant'
  const t = (value: string) => chinese ? value : (pricingText as Record<string, string>)[value] || value
  const unitLabel = (value: string) => chinese ? value : value.replace(/百万/g, 'million ').replace(/张/g, 'image').replace(/秒/g, 's').replace(/字符/g, 'character').replace(/像素/g, 'pixels')
  const exampleLabel = (value: string) => chinese ? value : value.replace(/秒输出费用/g, 's output cost:').replace(/秒输出/g, 's output').replace(/秒输入示例：/g, 's input: ')
  const [activeTab, setActiveTab] = useState<BillingTab>('all')
  const [activeProvider, setActiveProvider] = useState('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [billingData, setBillingData] = useState<PublicBillingDetailData | null>(null)

  const creditUnit = billingData?.creditUnit || 'Credits'

  const allModels = useMemo<ModelBillingDetailVO[]>(() => {
    const data = billingData
    if (!data) return []
    return [...data.llm, ...data.image, ...data.video, ...data.voice]
  }, [billingData])

  const modelsForActiveType = useMemo(
    () =>
      filterBillingModels(allModels, {
        modelType: activeTab,
        providerName: '',
        keyword: ''
      }),
    [allModels, activeTab]
  )

  const providerOptions = useMemo(
    () => listBillingModelProviders(modelsForActiveType),
    [modelsForActiveType]
  )
  const providerSelectOptions = [
    { label: t('全部厂商'), value: '' },
    ...providerOptions.map((provider) => ({ label: provider, value: provider }))
  ]

  const visibleModels = useMemo(
    () =>
      filterBillingModels(allModels, {
        modelType: activeTab,
        providerName: activeProvider,
        keyword
      }),
    [allModels, activeTab, activeProvider, keyword]
  )

  // 对齐原 watch(providerOptions)：切 tab 后厂商不在列表内时重置
  useEffect(() => {
    setActiveProvider((prev) => (prev && !providerOptions.includes(prev) ? '' : prev))
  }, [providerOptions])

  function formatInputPrice(model: ModelBillingDetailVO, mediaType: 'image' | 'video') {
    const unitPrice =
      mediaType === 'image' ? model.inputPricing?.imageUnitPrice : model.inputPricing?.videoUnitPrice
    const unit = t(mediaType === 'image' ? '张' : '秒')
    const n = Number(unitPrice)
    if (Number.isFinite(n) && n > 0) return `${n} ${creditUnit}/${unit}`
    const hasTierPrice = model.rules.some((rule) => {
      const value = mediaType === 'image' ? rule.inputImagePrice : rule.inputVideoPricePerSecond
      return Number(value) > 0
    })
    return t(hasTierPrice ? '按对应档位计费（见下方）' : '免费')
  }

  async function loadBilling() {
    setLoading(true)
    setLoadError(false)
    try {
      setBillingData(await publicBillingDetail({}))
    } catch {
      setLoadError(true)
      setBillingData(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadBilling()
     
  }, [])

  return (
    <div className="home-new-sub-page billing-page">
      <div className="page-content billing-page__inner">
        <header className="billing-page__header">
          <h2 className="billing-page__title">{t('计费说明')}</h2>
          <p className="billing-page__subtitle">{t('单位：')}{creditUnit}</p>
        </header>

        <div className="billing-page__toolbar">
          <div className="billing-page__tabs" role="tablist">
            {tabs.map((tab) => (
              <button
                key={tab.value}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.value}
                className={`billing-page__tab${activeTab === tab.value ? ' is-active' : ''}`}
                onClick={() => setActiveTab(tab.value)}
              >
                {t(tab.label)}
              </button>
            ))}
          </div>
          <div className="billing-page__filters">
            <div className="billing-page__provider-filter">
              <Select
                value={activeProvider}
                onChange={(value) => setActiveProvider(value)}
                className="billing-page__provider-select"
                classNames={{ popup: { root: 'billing-page__provider-popup' } }}
                options={providerSelectOptions}
                popupMatchSelectWidth={true}
                aria-label={t('模型厂商')}
                suffixIcon={<DownOutlined className="billing-page__provider-arrow" />}
              />
            </div>
            <div className="billing-page__search">
              <SearchOutlined className="billing-page__search-ico" />
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                type="search"
                className="billing-page__search-input"
                placeholder={t('搜索模型名称...')}
              />
            </div>
          </div>
        </div>

        {loading ? (
          <div className="billing-page__state">{t('加载计费详情中…')}</div>
        ) : loadError ? (
          <div className="billing-page__state billing-page__state--error">{t('加载失败，请稍后重试')} <button type="button" onClick={() => void loadBilling()}>{t('重试')}</button></div>
        ) : visibleModels.length === 0 ? (
          <div className="billing-page__state">{t('暂无匹配的计费模型')}</div>
        ) : (
          <div className="billing-page__cards">
            {visibleModels.map((model) => (
              <article key={`${model.modelCode}-${model.id}`} className="billing-page__card">
                <header className="billing-page__card-head">
                  <div className="billing-page__model-brand">
                    <div className="billing-page__model-logo">
                      {model.providerLogo ? (
                        <img src={model.providerLogo} alt={model.providerName || model.modelName} />
                      ) : (
                        <span>{(model.modelName || 'M').slice(0, 1)}</span>
                      )}
                    </div>
                    <div className="billing-page__model-meta">
                      <h2 className="billing-page__model-name">{model.modelName}</h2>
                    </div>
                  </div>
                  <div className="billing-page__model-tags">
                    {model.isFree === true && (
                      <span
                        className="billing-page__tag billing-page__tag--free"
                        role="status"
                        aria-label={t('该模型当前免费')}
                        title={t('该模型当前免费')}
                      >
                        {t('免费')}
                      </span>
                    )}
                    {model.meterTypeName && <span className="billing-page__tag">{t(model.meterTypeName)}</span>}
                    {model.generateMode && (
                      <span className="billing-page__tag billing-page__tag--muted">{model.generateMode}</span>
                    )}
                  </div>
                </header>

                {model.billingDesc && <p className="billing-page__card-desc">{t(model.billingDesc)}</p>}

                {hasInputPricing(model) && (
                  <div className="billing-page__input-pricing">
                    <p className="billing-page__input-pricing-title">{t('输入媒体计费（可能产生额外费用）')}</p>
                    <ul className="billing-page__input-pricing-list">
                      {model.inputPricing?.imageSupported && (
                        <li>
                          {t('图片输入：')}{formatInputPrice(model, 'image')}
                          {Number(model.inputPricing?.imageFreeCount) > 0 && (
                            <span>{chinese ? `，前 ${model.inputPricing.imageFreeCount} 张免费` : ` · First ${model.inputPricing.imageFreeCount} images free`}</span>
                          )}
                          {model.inputPricing?.imageMaxCount != null && (
                            <span>{chinese ? `，上限 ${model.inputPricing.imageMaxCount} 张` : ` · Max. ${model.inputPricing.imageMaxCount} images`}</span>
                          )}
                        </li>
                      )}
                      {model.inputPricing?.videoSupported && (
                        <li>
                          {t('视频输入：')}{formatInputPrice(model, 'video')}
                          {model.inputPricing?.videoMaxSeconds != null && (
                            <span>{chinese ? `，时长上限 ${model.inputPricing.videoMaxSeconds} 秒` : ` · Max. ${model.inputPricing.videoMaxSeconds}s`}</span>
                          )}
                          {model.inputPricing?.videoMaxCount != null && (
                            <span>{chinese ? `，段数上限 ${model.inputPricing.videoMaxCount}` : ` · Max. ${model.inputPricing.videoMaxCount} videos`}</span>
                          )}
                        </li>
                      )}
                    </ul>
                  </div>
                )}

                <div className="billing-page__scenarios">
                  {deriveBillingScenarios(model).map((scenario) => (
                    <section key={scenario.type} className="billing-page__scenario">
                      <header className="billing-page__scenario-head">
                        <div>
                          <h3 className="billing-page__scenario-title">{t(scenario.title)}</h3>
                          <p className="billing-page__scenario-applicability">{t(scenario.applicability)}</p>
                        </div>
                        <span className="billing-page__formula">{t(scenario.formula)}</span>
                      </header>

                      <div className="billing-page__table-wrap">
                        <table className="billing-page__table">
                          <thead>
                            <tr>
                              {scenario.columns.map((col) => (
                                <th key={col.key}>
                                  {t(col.label)}
                                  {col.unit && <span className="billing-page__th-unit"> ({unitLabel(col.unit)})</span>}
                                </th>
                              ))}
                              {model.meterType === 'PER_SECOND' && <th>{t('费用示例')}</th>}
                            </tr>
                          </thead>
                          <tbody>
                            {scenario.rules.map((rule, idx) => (
                              <tr key={`${model.modelCode}-${scenario.type}-${idx}`}>
                                {scenario.columns.map((col) => (
                                  <td key={`${col.key}-${idx}`}>{t(formatBillingCell(rule, col))}</td>
                                ))}
                                {model.meterType === 'PER_SECOND' && (
                                  <td className="billing-page__example">
                                    {exampleLabel(formatRuleExample(
                                      rule,
                                      scenario.type,
                                      creditUnit,
                                      model.inputPricing?.videoMaxSeconds
                                    )) || '—'}
                                  </td>
                                )}
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </section>
                  ))}
                </div>
              </article>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
