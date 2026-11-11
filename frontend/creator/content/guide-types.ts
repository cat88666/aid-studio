export type GuideSection = {
  id: string
  title: string
  summary: string
  paragraphs: string[]
  steps?: string[]
  tips?: string[]
  links?: { label: string; href: string }[]
}
