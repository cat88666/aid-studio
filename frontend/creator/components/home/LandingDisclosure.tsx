'use client'

import { useId, useState, type ReactNode } from 'react'
import { DownOutlined } from '@ant-design/icons'

export function LandingDisclosure({ title, children }: { title: string; children: ReactNode }) {
  const id = useId()
  const [open, setOpen] = useState(false)
  return (
    <div className="creator-disclosure">
      <button type="button" aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)}>
        {title}<DownOutlined aria-hidden="true" />
      </button>
      <div id={id} hidden={!open}>{open && children}</div>
    </div>
  )
}
