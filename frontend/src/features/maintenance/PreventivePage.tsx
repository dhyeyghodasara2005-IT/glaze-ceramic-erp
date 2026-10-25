import { useCallback, useState } from 'react'
import { Button, Input, Select, Switch } from 'antd'
import { CheckOutlined, PlusOutlined, ScheduleOutlined, WarningOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import { api } from '../../api/client'
import type { DueStatus, PmTaskSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { shortDate } from '../store/format'
import { fmt } from '../dashboard/charts'
import { useMaintenanceMachines, usePmOverview, usePmTasks } from './maintenanceApi'
import { DueTag } from './maintenanceParts'
import PmDoneModal from './PmDoneModal'
import PmTaskDrawer from './PmTaskDrawer'
import PmTaskFormDrawer from './PmTaskFormDrawer'

const GROUPS: DueStatus[] = ['OVERDUE', 'TODAY', 'WEEK', 'LATER']
const fetchTask = async (id: number) => (await api.get<PmTaskSummary>(`/maintenance/pm-tasks/${id}`)).data

/**
 * /maintenance/preventive - the routine jobs of every machine, the most urgent first: overdue, due today, this week,
 * later. Maintenance marks them done here; the next due date then moves forward by itself.
 */
export default function PreventivePage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  const [search, setSearch] = useState<string>()
  const [machineId, setMachineId] = useState<number>()
  const [includeInactive, setIncludeInactive] = useState(false)
  const [showLater, setShowLater] = useState(false)
  const [creating, setCreating] = useState(false)
  const [openId, setOpenId] = useState<number>()
  const [doing, setDoing] = useState<PmTaskSummary>()
  const mayEnter = can('MAINTENANCE_ENTRY')

  const tasks = usePmTasks(currentPlant ? { plantId: currentPlant.id, search, machineId, includeInactive } : null)
  const overview = usePmOverview(currentPlant?.id)
  const machines = useMaintenanceMachines(currentPlant?.id)

  const openFromUrl = useCallback((item?: PmTaskSummary) => {
    if (item) setOpenId(item.id)
    else if (mayEnter) setCreating(true)
  }, [mayEnter])
  useOpenFromUrl({ fetchOne: fetchTask, onOpen: openFromUrl })

  if (!currentPlant) return <NoPlant />

  const all = tasks.data ?? []
  const byGroup = (g: DueStatus) => all.filter((x) => (g === 'LATER' ? x.due === 'LATER' || !x.active : x.active && x.due === g))
  const o = overview.data
  const newButton = mayEnter && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>{t('pm.new')}</Button>

  return (
    <>
      <PageHeader title={t('pm.pageTitle')} subtitle={t('pm.pageSubtitle')} icon={<ScheduleOutlined />} extra={newButton} />

      {o && (
        <Stagger className="dash-headline">
          <StaggerItem className={`dash-tile${o.overdue ? ' warn' : ''}`}>
            <div className="stat-label">{t('pm.group.OVERDUE')}</div>
            <div className="dash-tile-value">{o.overdue}</div>
            <div className="dash-tile-sub">
              {o.oldest ? <><WarningOutlined /> {t('pm.oldest', { what: `${o.oldest.machine.name}: ${o.oldest.title}`, count: o.oldest.daysOverdue })}</> : t('pm.noneOverdue')}
            </div>
          </StaggerItem>
          <StaggerItem className="dash-tile">
            <div className="stat-label">{t('pm.group.TODAY')}</div>
            <div className="dash-tile-value">{o.dueToday}</div>
            <div className="dash-tile-sub">{t('pm.thisWeekN', { count: o.dueWeek })}</div>
          </StaggerItem>
          <StaggerItem className="dash-tile">
            <div className="stat-label">{t('pm.onTimeTitle')}</div>
            <div className="dash-tile-value">{o.onTimePct == null ? '–' : fmt(o.onTimePct, 1)}<span className="dash-unit">{o.onTimePct == null ? '' : '%'}</span></div>
            <div className="dash-tile-sub">{t('pm.onTimeSub', { on: o.onTimeLast30, count: o.doneLast30 })}</div>
          </StaggerItem>
          <StaggerItem className="dash-tile">
            <div className="stat-label">{t('pm.activeJobs')}</div>
            <div className="dash-tile-value">{o.activeJobs}</div>
            <div className="dash-tile-sub">{t('pm.activeJobsSub')}</div>
          </StaggerItem>
        </Stagger>
      )}

      <FadeIn delay={0.06} className="toolbar">
        <Input.Search allowClear placeholder={t('pm.search')} style={{ maxWidth: 260 }} onSearch={(v) => setSearch(v.trim() || undefined)} />
        <Select allowClear placeholder={t('pm.allMachines')} style={{ minWidth: 200 }} value={machineId} onChange={setMachineId}
          options={(machines.data ?? []).map((m) => ({ value: m.id, label: m.name }))} showSearch={{ optionFilterProp: 'label' }} />
        <label className="pm-toggle"><Switch size="small" checked={includeInactive} onChange={setIncludeInactive} /> {t('pm.showOff')}</label>
      </FadeIn>

      {tasks.data && all.length === 0 && (
        <div className="panel"><EmptyState title={t('pm.empty')} action={newButton} /></div>
      )}

      <div className={tasks.isPlaceholderData ? 'refreshing' : undefined}>
        {GROUPS.map((g, gi) => {
          const items = byGroup(g)
          if (!items.length) return null
          const hidden = g === 'LATER' && !showLater
          return (
            <FadeIn key={g} delay={0.08 + gi * 0.03} className={`pm-group ${g.toLowerCase()}`}>
              <h2 className="section-title">{t(`pm.group.${g}`)} <span className="pm-count">{items.length}</span></h2>
              {hidden ? (
                <Button type="dashed" block onClick={() => setShowLater(true)}>{t('pm.showLater', { count: items.length })}</Button>
              ) : (
                <div className="pm-rows">
                  {items.map((x) => (
                    <div key={x.id} className={`pm-row ${x.active ? x.due.toLowerCase() : 'off'}`} role="button" tabIndex={0}
                      onClick={() => setOpenId(x.id)} onKeyDown={(e) => e.key === 'Enter' && setOpenId(x.id)}>
                      <div className="pm-row-main">
                        <div className="cell-title">{x.title}</div>
                        <div className="cell-sub">
                          {x.machine.name} · {t(`pm.kinds.${x.kind}`)} · {t('pm.everyShort', { count: x.everyDays })}
                          {x.needsStop && ` · ${t('pm.stopTag')}`}
                        </div>
                      </div>
                      <div className="pm-row-due">
                        <DueTag task={x} />
                        <div className="cell-sub">{shortDate(x.nextDue)}</div>
                      </div>
                      {mayEnter && x.active && (
                        <Button icon={<CheckOutlined />} onClick={(e) => { e.stopPropagation(); setDoing(x) }}>{t('pm.markDone')}</Button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </FadeIn>
          )
        })}
      </div>

      <PmTaskFormDrawer open={creating} plantId={currentPlant.id} onClose={() => setCreating(false)} />
      <PmTaskDrawer id={openId} onClose={() => setOpenId(undefined)} />
      <PmDoneModal task={doing} plantId={currentPlant.id} onClose={() => setDoing(undefined)} />
    </>
  )
}
