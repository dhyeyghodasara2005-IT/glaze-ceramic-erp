import { Fragment, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Input, Modal, Spin, type InputRef } from 'antd'
import { ClockCircleOutlined, EnterOutlined, PlusOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useDebounced } from '../components/useDebounced'
import { ALL_PAGES, pageForType } from '../layout/menu'
import { loadRecent, pushRecent, type RecentItem } from './recent'

/** Matches SearchHit.java */
interface SearchHit {
  type: string
  id: number
  code?: string
  title: string
  subtitle?: string
  active: boolean
}

/** One selectable row in the palette. */
interface Row {
  key: string
  group: string
  icon: ReactNode
  title: string
  code?: string
  subtitle?: string
  inactive?: boolean
  url: string
  recent: RecentItem
}

/** Wraps the part of `text` that matches `query` in <mark> so the user sees why it matched. */
function highlight(text: string, query: string) {
  const q = query.trim()
  if (!q) return <span>{text}</span>
  const i = text.toLowerCase().indexOf(q.toLowerCase())
  if (i < 0) return <span>{text}</span>
  // One <span> around all parts, so the row's flex gap doesn't split the word.
  return (
    <span>
      {text.slice(0, i)}
      <mark>{text.slice(i, i + q.length)}</mark>
      {text.slice(i + q.length)}
    </span>
  )
}

/**
 * Global "Go To" / search box. Opens with Ctrl+K / ⌘K, Alt+G or "/".
 * Everything works from the keyboard: type, ↑ ↓ to move, Enter to open, Esc to close.
 */
export default function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const navigate = useNavigate()
  const inputRef = useRef<InputRef>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const [recent, setRecent] = useState<RecentItem[]>([])
  const q = query.trim()
  const debounced = useDebounced(q)

  // Fresh start every time it opens.
  useEffect(() => {
    if (!open) return
    setQuery('')
    setActiveIndex(0)
    setRecent(loadRecent())
  }, [open])

  const { data: hits = [], isFetching } = useQuery({
    queryKey: ['search', debounced],
    queryFn: async () => (await api.get<SearchHit[]>('/search', { params: { q: debounced, limit: 5 } })).data,
    enabled: open && debounced.length > 0,
    staleTime: 10_000,
    placeholderData: keepPreviousData,
  })

  // Build the rows shown, in order. Pages and "create" actions are filtered here in the browser;
  // records come from the server.
  const rows = useMemo<Row[]>(() => {
    const lower = q.toLowerCase()
    const pages = ALL_PAGES.filter((p) => !p.permission || can(p.permission))
    const out: Row[] = []

    if (!q) {
      recent.forEach((r) =>
        out.push({ key: `recent:${r.key}`, group: t('search.recent'), icon: <ClockCircleOutlined />,
          title: r.title, subtitle: r.subtitle, url: r.url, recent: r }),
      )
    }

    pages
      .filter((p) => !q || t(p.labelKey).toLowerCase().includes(lower))
      .slice(0, q ? 4 : 20)
      .forEach((p) => {
        const title = t(p.labelKey)
        out.push({ key: `page:${p.path}`, group: t('search.goTo'), icon: p.icon, title, url: p.path,
          recent: { key: `page:${p.path}`, title, url: p.path, type: 'page' } })
      })

    pages
      .filter((p) => p.create && can(p.create.permission))
      .filter((p) => !q || t(p.create!.labelKey).toLowerCase().includes(lower))
      .slice(0, q ? 3 : 20)
      .forEach((p) => {
        const title = t(p.create!.labelKey)
        const url = `${p.path}?new=1`
        out.push({ key: `new:${p.path}`, group: t('search.create'), icon: <PlusOutlined />, title, url,
          recent: { key: `new:${p.path}`, title, url, type: 'new' } })
      })

    if (q) {
      hits.forEach((h) => {
        const page = pageForType(h.type)
        if (!page) return // a result type with no page yet
        const url = `${page.path}?open=${h.id}`
        out.push({ key: `${h.type}:${h.id}`, group: t(page.labelKey), icon: page.icon, title: h.title,
          code: h.code, subtitle: h.subtitle, inactive: !h.active, url,
          recent: { key: `${h.type}:${h.id}`, title: h.title, subtitle: h.code ?? h.subtitle, url, type: h.type } })
      })
    }
    return out
  }, [q, hits, recent, can, t])

  // Keep the highlighted row valid and visible.
  useEffect(() => setActiveIndex(0), [q])
  useEffect(() => {
    listRef.current?.querySelector(`[data-index="${activeIndex}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [activeIndex])

  const choose = (row: Row) => {
    pushRecent(row.recent)
    onClose()
    navigate(row.url)
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActiveIndex((i) => (rows.length ? (i + 1) % rows.length : 0))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIndex((i) => (rows.length ? (i - 1 + rows.length) % rows.length : 0))
    } else if (e.key === 'Enter' && rows[activeIndex]) {
      e.preventDefault()
      choose(rows[activeIndex])
    }
  }

  const waitingForServer = q.length > 0 && (isFetching || debounced !== q)
  let lastGroup = ''

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      closable={false}
      width={640}
      style={{ top: '12vh' }}
      className="palette-modal"
      destroyOnHidden
      afterOpenChange={(isOpen) => isOpen && inputRef.current?.focus()}
    >
      <div className="palette-input-row">
        <SearchOutlined className="palette-input-icon" />
        <Input
          ref={inputRef}
          variant="borderless"
          size="large"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={t('search.placeholder')}
          aria-label={t('search.button')}
          autoComplete="off"
          spellCheck={false}
        />
        {waitingForServer ? <Spin size="small" /> : <kbd className="kbd">esc</kbd>}
      </div>

      <div className="palette-list" ref={listRef} role="listbox">
        {rows.length === 0 && !waitingForServer && (
          <div className="palette-empty">{t('search.noResults', { q })}</div>
        )}
        {rows.map((row, index) => {
          const showGroup = row.group !== lastGroup
          lastGroup = row.group
          return (
            <Fragment key={row.key}>
              {showGroup && <div className="palette-group">{row.group}</div>}
              <div
                role="option"
                aria-selected={index === activeIndex}
                data-index={index}
                className={`palette-row${index === activeIndex ? ' active' : ''}${row.inactive ? ' inactive' : ''}`}
                onMouseMove={() => setActiveIndex(index)}
                onClick={() => choose(row)}
              >
                <span className="palette-row-icon">{row.icon}</span>
                <span className="palette-row-text">
                  <span className="palette-row-title">
                    {highlight(row.title, q)}
                    {row.code && row.code !== row.title && <span className="code-badge">{highlight(row.code, q)}</span>}
                  </span>
                  {row.subtitle && <span className="palette-row-sub">{row.subtitle}</span>}
                </span>
                {index === activeIndex ? <EnterOutlined className="palette-row-enter" /> : <RightOutlined className="palette-row-arrow" />}
              </div>
            </Fragment>
          )
        })}
      </div>

      <div className="palette-footer">
        <span><kbd className="kbd">↑</kbd><kbd className="kbd">↓</kbd> {t('search.hintMove')}</span>
        <span><kbd className="kbd">↵</kbd> {t('search.hintOpen')}</span>
        <span><kbd className="kbd">Alt</kbd><kbd className="kbd">G</kbd> {t('search.hintAnywhere')}</span>
      </div>
    </Modal>
  )
}
