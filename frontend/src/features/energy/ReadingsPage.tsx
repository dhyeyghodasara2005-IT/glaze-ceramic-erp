import { useEffect, useState } from 'react'
import { App, Button, Checkbox, DatePicker, Input, InputNumber, Popconfirm, Segmented, Tag } from 'antd'
import { CheckCircleFilled, DeleteOutlined, FormOutlined, WarningOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import { METER_KINDS, type RoundLine } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { fmt } from '../dashboard/charts'
import { useDeleteReading, useRound, useSaveRound } from './energyApi'
import { useUnit } from './energyParts'

/** What is typed for one meter: the number on it (counters) or the amount used. */
interface Entry {
  value?: number
  restarted?: boolean
  note?: string
}

/** A use this far from the usual one is worth a second look (and a note): half as much again, or half of it. */
const HIGH = 1.5
const LOW = 0.5

/** The use the typed number means: reading minus the one before (or from 0 for a new meter), or the amount itself. */
function usedOf(line: RoundLine, e: Entry) {
  if (e.value == null) return undefined
  if (!line.meter.counter || e.restarted) return e.value
  return line.previous ? e.value - line.previous.reading : 0
}

function savedEntry(line: RoundLine): Entry {
  const s = line.saved
  return s ? { value: line.meter.counter ? s.reading : s.used, restarted: s.restarted, note: s.note } : {}
}

/**
 * /energy/readings - the reading round. After each shift a supervisor types what each meter shows (and the coal or
 * diesel used); the use appears at once, with a warning when it is far from the usual. The main meters, read the
 * next morning, come with the day's last shift.
 */
export default function ReadingsPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can, currentPlant } = useAuth()
  const unit = useUnit()
  const mayEnter = can('METER_ENTRY')
  const [slot, setSlot] = useState<{ date: string; shiftId?: number }>()
  const [entries, setEntries] = useState<Record<number, Entry>>({})
  const round = useRound(currentPlant ? { plantId: currentPlant.id, ...slot } : null)
  const save = useSaveRound()
  const remove = useDeleteReading()
  const r = round.data

  // the round to read now comes from the server (the shift that ended last); then the user may pick another
  useEffect(() => {
    if (r && !slot) setSlot({ date: r.date, shiftId: r.shift?.id })
  }, [r, slot])
  // what is saved fills the boxes whenever another round is shown
  const roundKey = r ? `${r.date}/${r.shift?.id ?? 0}` : ''
  useEffect(() => {
    if (!r) return
    setEntries(Object.fromEntries(r.lines.map((l) => [l.meter.id, savedEntry(l)])))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roundKey, r?.lines])

  if (!currentPlant) return <NoPlant />

  const lines = r?.lines ?? []
  const entry = (l: RoundLine) => entries[l.meter.id] ?? {}
  const change = (l: RoundLine, patch: Entry) => setEntries((all) => ({ ...all, [l.meter.id]: { ...all[l.meter.id], ...patch } }))
  const lower = (l: RoundLine) => {
    const e = entry(l)
    return l.meter.counter && !e.restarted && e.value != null && l.previous != null && e.value < l.previous.reading
  }
  const dirty = (l: RoundLine) => {
    const e = entry(l)
    const s = savedEntry(l)
    return e.value != null && (e.value !== s.value || !!e.restarted !== !!s.restarted || (e.note ?? '') !== (s.note ?? ''))
  }
  const changed = lines.filter(dirty)
  // diesel, propane and LNG are typed only when some was used: they don't count as unread
  const due = lines.filter((l) => l.meter.everyRound)
  const read = due.filter((l) => l.saved).length
  const daily = lines.some((l) => l.meter.readEvery === 'DAY')

  const onSave = async () => {
    if (changed.some(lower)) {
      message.error(t('energy.fixLower'))
      return
    }
    try {
      await save.mutateAsync({
        plantId: currentPlant.id, date: r!.date, shiftId: r!.shift?.id,
        lines: changed.map((l) => {
          const e = entry(l)
          return l.meter.counter
            ? { meterId: l.meter.id, reading: e.value, restarted: !!e.restarted, note: e.note?.trim() || undefined }
            : { meterId: l.meter.id, used: e.value, note: e.note?.trim() || undefined }
        }),
      })
      message.success(t('energy.savedN', { count: changed.length }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onDelete = async (id: number) => {
    try {
      await remove.mutateAsync(id)
      message.success(t('energy.deleted'))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <>
      <PageHeader title={t('energy.readingsTitle')} subtitle={t('energy.readingsSubtitle')} icon={<FormOutlined />} />

      {r && (
        <FadeIn delay={0.04} className="toolbar round-pick">
          <DatePicker value={dayjs(r.date)} format="DD MMM YYYY" allowClear={false} disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setSlot({ date: d.format('YYYY-MM-DD'), shiftId: slot?.shiftId })} />
          {r.shifts.length > 0 && (
            <Segmented<number> value={r.shift?.id ?? 0} onChange={(id) => setSlot({ date: r.date, shiftId: id || undefined })}
              options={[...r.shifts.map((s) => ({ value: s.id, label: `${s.code} · ${s.name}` })),
                { value: 0, label: t('energy.dailyOnly') }]} />
          )}
          {due.length > 0 && <span className="cell-sub">{t('energy.readOfN', { read, count: due.length })}</span>}
        </FadeIn>
      )}
      {r && daily && <p className="round-note">{t('energy.dailyNote', { date: dayjs(r.date).format('D MMM') })}</p>}

      {r && lines.length === 0 && (
        <div className="panel"><EmptyState title={t('energy.nothingToRead')} hint={t('energy.nothingToReadHint')} /></div>
      )}

      <div className={round.isPlaceholderData ? 'refreshing' : undefined}>
        {METER_KINDS.map((kind) => {
          const group = lines.filter((l) => l.meter.kind === kind)
          if (!group.length) return null
          return (
            <section key={kind} className="round-group">
              <h2 className="section-title">{t(`energy.kinds.${kind}`)}</h2>
              <div className="round-rows">
                {group.map((l) => {
                  const e = entry(l)
                  const used = usedOf(l, e)
                  const u = unit(l.meter.unit)
                  const isLower = lower(l)
                  const unusual = !isLower && used != null && used > 0 && l.usual != null && (used > l.usual * HIGH || used < l.usual * LOW)
                  return (
                    <div key={l.meter.id} className={`round-row${isLower ? ' bad' : unusual ? ' odd' : l.saved && !dirty(l) ? ' done' : ''}`}>
                      <div className="round-meter">
                        <div className="cell-title">
                          {l.meter.name} {l.meter.readEvery === 'DAY' && <Tag bordered={false}>{t('energy.every.DAY')}</Tag>}
                        </div>
                        <div className="cell-sub">
                          {l.meter.counter
                            ? l.previous
                              ? t('energy.before', { n: fmt(l.previous.reading, 3), when: dayjs(l.previous.readOn).format('D MMM') + (l.previous.shiftCode ? ` ${l.previous.shiftCode}` : '') })
                              : t('energy.firstReading')
                            : l.meter.everyRound ? t('energy.amountUsed') : t('energy.whenUsed')}
                        </div>
                      </div>
                      <InputNumber<number> min={0} value={e.value} disabled={!mayEnter} inputMode="decimal"
                        suffix={u} placeholder={l.meter.counter ? t('energy.onTheMeter') : t('energy.used')} className="round-input"
                        onChange={(v) => change(l, { value: v ?? undefined })} />
                      <div className="round-used">
                        {used != null && !isLower && <b>{t('energy.usedN', { n: fmt(used, 2), unit: u })}</b>}
                        {isLower && <span className="round-warn bad"><WarningOutlined /> {t('energy.lowerThanBefore')}</span>}
                        {unusual && <span className="round-warn"><WarningOutlined /> {t('energy.usually', { n: fmt(l.usual, 0), unit: u })}</span>}
                        {l.saved && !dirty(l) && (
                          <span className="round-saved">
                            <CheckCircleFilled /> {t('energy.savedBy', { by: l.saved.by ?? '' })}
                            {mayEnter && (
                              <Popconfirm title={t('energy.deleteConfirm')} okText={t('common.delete')} cancelText={t('common.cancel')}
                                onConfirm={() => onDelete(l.saved!.id)}>
                                <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={t('common.delete')} />
                              </Popconfirm>
                            )}
                          </span>
                        )}
                      </div>
                      {l.meter.counter && (isLower || e.restarted) && (
                        <Checkbox checked={!!e.restarted} onChange={(ev) => change(l, { restarted: ev.target.checked })}>
                          {t('energy.restarted')}
                        </Checkbox>
                      )}
                      {(unusual || e.restarted || e.note) && (
                        <Input maxLength={255} value={e.note} placeholder={t('energy.notePlaceholder')}
                          onChange={(ev) => change(l, { note: ev.target.value })} />
                      )}
                    </div>
                  )
                })}
              </div>
            </section>
          )
        })}
      </div>

      {mayEnter && lines.length > 0 && (
        <div className="loading-confirm-bar round-save">
          <Button type="primary" block loading={save.isPending} disabled={!changed.length} onClick={onSave}>
            {changed.length ? t('energy.saveN', { count: changed.length }) : t('energy.nothingChanged')}
          </Button>
        </div>
      )}
    </>
  )
}
