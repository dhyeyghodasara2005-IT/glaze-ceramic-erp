import { Tag } from 'antd'
import { ArrowRightOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import EmptyState from '../../../components/EmptyState'
import type { Machine, MachineLink } from '../../../api/types'
import { layoutColumns } from './layout'
import { STAGE_COLOR } from './stageColor'

/**
 * The plant drawn left to right: each column is one step further down the line, and each card says where its
 * tiles go next (→ KILN-1). Columns follow the arrows, not the machine types, so a compensator shows up where it
 * really stands. On a phone the columns become rows, one step under the other.
 */
export default function MachineLayout({ machines, links, onOpen }: {
  machines: Machine[]
  links: MachineLink[]
  onOpen?: (m: Machine) => void
}) {
  const { t } = useTranslation()
  const { columns, unlinked } = layoutColumns(machines, links)
  const codeOf = new Map(machines.map((m) => [m.id, m.code]))
  const feeds = (id: number) => links.filter((l) => l.fromMachineId === id).map((l) => codeOf.get(l.toMachineId) ?? '?')

  const node = (m: Machine) => {
    const to = feeds(m.id)
    return (
      <button
        key={m.id}
        type="button"
        className={`layout-node${m.active ? '' : ' inactive'}`}
        onClick={onOpen ? () => onOpen(m) : undefined}
        disabled={!onOpen}
      >
        <span className="layout-node-head">
          <span className="code-badge">{m.code}</span>
          <Tag color={STAGE_COLOR[m.type]} className="layout-node-type">{t(`machineType.${m.type}`)}</Tag>
        </span>
        <span className="layout-node-name">{m.name}</span>
        {to.length > 0 && (
          <span className="layout-node-feeds">
            <ArrowRightOutlined /> {to.join(', ')}
          </span>
        )}
        {!m.active && <span className="layout-node-feeds">{m.replacedByCode ? t('machine.replacedBy', { code: m.replacedByCode }) : t('common.inactive')}</span>}
      </button>
    )
  }

  return (
    <div className="layout-wrap">
      {columns.length === 0 ? (
        <EmptyState title={t('machine.layoutEmpty')} hint={t('machine.layoutEmptyHint')} />
      ) : (
        <div className="layout-board" role="list" aria-label={t('machine.layout')}>
          {columns.map((col, i) => (
            <div className="layout-col" key={i} role="listitem">
              <div className="layout-col-title">{t('machine.step', { n: i + 1 })}</div>
              <div className="layout-col-nodes">{col.map(node)}</div>
            </div>
          ))}
        </div>
      )}
      {unlinked.length > 0 && (
        <div className="layout-unlinked">
          <div className="layout-col-title">{t('machine.notLinked')}</div>
          <div className="layout-col-nodes layout-wrap-nodes">{unlinked.map(node)}</div>
        </div>
      )}
    </div>
  )
}
