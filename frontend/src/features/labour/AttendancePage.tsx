import { useMemo, useState } from 'react'
import { App, Button, DatePicker, Input, InputNumber, Popconfirm, Radio, Segmented, Select, Tag } from 'antd'
import { CheckCircleFilled, CloseOutlined, ScheduleOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import { ATTENDANCE_STATUSES, DEPARTMENTS, type AttendanceRow, type AttendanceStatus, type Department } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { useAttendance, useSaveAttendance } from './labourApi'

/** What the screen shows for one worker: the saved mark, or what the supervisor has just tapped. */
interface Entry {
  /** null = not marked (or the mark taken back) */
  status: AttendanceStatus | null
  ot: number
  note?: string
}

const savedEntry = (r: AttendanceRow): Entry =>
  r.mark ? { status: r.mark.status, ot: r.mark.otHours, note: r.mark.note } : { status: null, ot: 0 }

const same = (a: Entry, b: Entry) => a.status === b.status && a.ot === b.ot && (a.note ?? '') === (b.note ?? '')

/** A day that counts as worked can have overtime; absent and leave can't. */
const worked = (s: AttendanceStatus | null) => s === 'PRESENT' || s === 'HALF'

/**
 * /labour/attendance - the day's attendance. The supervisor goes down the signed cards and taps present, half day,
 * absent or leave for each worker, with the overtime hours. Only what changed is saved, so two supervisors can mark
 * their own sections of the same day; a saved mark shows who marked it (the signature).
 */
export default function AttendancePage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can, currentPlant } = useAuth()
  const mayMark = can('ATTENDANCE_ENTRY')
  const [date, setDate] = useState<string>()       // none = today's production date, as the server says
  const [department, setDepartment] = useState<Department>()
  const [shiftId, setShiftId] = useState<number>()
  const [search, setSearch] = useState('')
  // only the rows touched since the last save: a refresh of the sheet never wipes what is being typed
  const [edits, setEdits] = useState<Record<number, Entry>>({})
  const sheet = useAttendance(currentPlant ? { plantId: currentPlant.id, date } : null)
  const save = useSaveAttendance()
  const s = sheet.data

  const allRows = useMemo(() => s?.rows ?? [], [s])
  const entry = (r: AttendanceRow) => edits[r.workerId] ?? savedEntry(r)
  const dirty = (r: AttendanceRow) => edits[r.workerId] !== undefined && !same(edits[r.workerId], savedEntry(r))
  const change = (r: AttendanceRow, patch: Partial<Entry>) =>
    setEdits((all) => {
      const next = { ...(all[r.workerId] ?? savedEntry(r)), ...patch }
      if (!worked(next.status)) next.ot = 0
      return { ...all, [r.workerId]: next }
    })

  const undo = (r: AttendanceRow) =>
    setEdits((all) => {
      const next = { ...all }
      delete next[r.workerId]
      return next
    })

  const q = search.trim().toLowerCase()
  const rows = allRows.filter((r) => (!department || r.department === department)
    && (!q || r.name.toLowerCase().includes(q) || r.code.toLowerCase().includes(q) || (r.contractorName ?? '').toLowerCase().includes(q)))
  const changed = allRows.filter(dirty)
  const shift = shiftId ?? s?.shiftNow

  // departments that have workers today, each with how many are still to mark
  const departments = DEPARTMENTS.flatMap((d) => {
    const of = allRows.filter((r) => r.department === d)
    return of.length ? [{ d, left: of.filter((r) => entry(r).status === null).length, total: of.length }] : []
  })
  const counts = (list: AttendanceRow[]) => {
    const c = { PRESENT: 0, HALF: 0, ABSENT: 0, LEAVE: 0, none: 0 }
    for (const r of list) {
      const st = entry(r).status
      if (st) c[st]++
      else c.none++
    }
    return c
  }
  const c = counts(rows)

  const markRestPresent = () =>
    setEdits((all) => {
      const next = { ...all }
      for (const r of rows) if (entry(r).status === null) next[r.workerId] = { status: 'PRESENT', ot: 0 }
      return next
    })

  const onSave = async () => {
    try {
      await save.mutateAsync({
        plantId: currentPlant!.id, date: s!.date, shiftId: shift,
        lines: changed.map((r) => {
          const e = entry(r)
          return { workerId: r.workerId, status: e.status ?? undefined, otHours: worked(e.status) ? e.ot : 0, note: e.note?.trim() || undefined }
        }),
      })
      message.success(t('labour.savedN', { count: changed.length }))
      setEdits({})
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const pickDate = (d: string) => {
    if (changed.length && !window.confirm(t('labour.leaveUnsaved'))) return
    setEdits({})
    setShiftId(undefined)
    setDate(d)
  }

  if (!currentPlant) return <NoPlant />

  return (
    <>
      <PageHeader title={t('labour.attendanceTitle')} subtitle={t('labour.attendanceSubtitle')} icon={<ScheduleOutlined />} />

      {s && (
        <FadeIn delay={0.04} className="toolbar round-pick">
          <DatePicker value={dayjs(s.date)} format="ddd, DD MMM YYYY" allowClear={false} disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && pickDate(d.format('YYYY-MM-DD'))} />
          {s.shifts.length > 0 && mayMark && (
            <Segmented<number> value={shift ?? 0} onChange={(id) => setShiftId(id || undefined)}
              options={[...s.shifts.map((x) => ({ value: x.id, label: `${x.code} · ${x.name}` })), { value: 0, label: t('labour.noShift') }]} />
          )}
          <Select<Department> allowClear placeholder={t('labour.allDepartments')} value={department} onChange={setDepartment}
            style={{ minWidth: 200 }} popupMatchSelectWidth={false}
            options={departments.map((x) => ({
              value: x.d,
              label: `${t(`labour.dept.${x.d}`)} · ${x.left ? t('labour.leftN', { count: x.left }) : t('labour.allMarked')}`,
            }))} />
          <Input allowClear prefix={<SearchOutlined />} placeholder={t('labour.searchSheet')} value={search}
            onChange={(e) => setSearch(e.target.value)} style={{ maxWidth: 240 }} />
        </FadeIn>
      )}

      {s && allRows.length === 0 && (
        <div className="panel"><EmptyState title={t('labour.noWorkers')} hint={t('labour.noWorkersHint')} /></div>
      )}

      {rows.length > 0 && (
        <FadeIn delay={0.06} className="att-counts">
          {ATTENDANCE_STATUSES.map((st) => (
            <span key={st} className={`att-count ${st.toLowerCase()}`}><b>{c[st]}</b> {t(`labour.status.${st}`)}</span>
          ))}
          <span className={`att-count none${c.none ? ' left' : ''}`}><b>{c.none}</b> {t('labour.notMarked')}</span>
          {mayMark && c.none > 0 && (
            <Button size="small" onClick={markRestPresent}>{t('labour.restPresent', { count: c.none })}</Button>
          )}
        </FadeIn>
      )}

      <div className={sheet.isPlaceholderData ? 'refreshing' : undefined}>
        {DEPARTMENTS.map((d) => {
          const group = rows.filter((r) => r.department === d)
          if (!group.length) return null
          return (
            <section key={d} className="round-group">
              <h2 className="section-title">{t(`labour.dept.${d}`)} <span className="cell-sub">{group.length}</span></h2>
              <div className="round-rows">
                {group.map((r) => {
                  const e = entry(r)
                  const isDirty = dirty(r)
                  return (
                    <div key={r.workerId} className={`round-row att-row${e.status ? ` ${e.status.toLowerCase()}` : ''}${isDirty ? ' changed' : ''}`}>
                      <div className="round-meter">
                        <div className="cell-title">
                          {r.name} <span className="code-badge">{r.code}</span>
                          {r.punch === 'FINGERPRINT' && <Tag bordered={false}>{t('labour.punch.FINGERPRINT')}</Tag>}
                        </div>
                        <div className="cell-sub">{[r.job, r.contractorName ?? t('labour.ownRolls')].filter(Boolean).join(' · ')}</div>
                      </div>
                      <Radio.Group optionType="button" buttonStyle="solid" value={e.status} disabled={!mayMark} className="att-pick"
                        onChange={(ev) => change(r, { status: ev.target.value })}
                        options={ATTENDANCE_STATUSES.map((st) => ({ value: st, label: t(`labour.short.${st}`), title: t(`labour.status.${st}`) }))} />
                      <div className="round-used">
                        {worked(e.status) && (
                          <InputNumber<number> min={0} max={16} step={0.5} precision={1} value={e.ot || undefined} disabled={!mayMark}
                            inputMode="decimal" placeholder="0" suffix={t('labour.otHours')} className="att-ot"
                            onChange={(v) => change(r, { ot: v ?? 0 })} aria-label={t('labour.ot')} />
                        )}
                        {r.mark && !isDirty && (
                          <span className="round-saved">
                            <CheckCircleFilled /> {t('labour.markedBy', { by: r.mark.by ?? '' })}
                            {r.mark.shiftCode && ` · ${r.mark.shiftCode}`}
                          </span>
                        )}
                        {isDirty && <span className="round-warn">{e.status ? t('labour.notSaved') : t('labour.willClear')}</span>}
                        {mayMark && isDirty && (
                          <Button type="text" size="small" icon={<UndoOutlined />} onClick={() => undo(r)}>{t('labour.undo')}</Button>
                        )}
                        {mayMark && r.mark && !isDirty && (
                          <Popconfirm title={t('labour.clearConfirm')} okText={t('labour.clear')} cancelText={t('common.cancel')}
                            onConfirm={() => change(r, { status: null })}>
                            <Button type="text" size="small" icon={<CloseOutlined />} aria-label={t('labour.clear')} />
                          </Popconfirm>
                        )}
                      </div>
                      {(e.status === 'LEAVE' || e.status === 'ABSENT' || e.note) && (
                        <Input maxLength={255} value={e.note} disabled={!mayMark} placeholder={t('labour.notePlaceholder')}
                          onChange={(ev) => change(r, { note: ev.target.value })} />
                      )}
                    </div>
                  )
                })}
              </div>
            </section>
          )
        })}
        {s && allRows.length > 0 && rows.length === 0 && <p className="cell-sub">{t('labour.noneMatch')}</p>}
      </div>

      {mayMark && allRows.length > 0 && (
        <div className="loading-confirm-bar round-save">
          <Button type="primary" block loading={save.isPending} disabled={!changed.length} onClick={onSave}>
            {changed.length ? t('labour.saveN', { count: changed.length }) : t('labour.nothingChanged')}
          </Button>
        </div>
      )}
    </>
  )
}
