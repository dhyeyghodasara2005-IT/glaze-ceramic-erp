import { useEffect, useState } from 'react'
import { App, Button, Drawer, Input, InputNumber, Space } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import { errorMessage } from '../../api/errors'
import type { MonthlyCostHead, MonthlyCostRow } from '../../api/types'
import { useDrawerSize } from '../../components/useDrawerSize'
import { rupees } from '../labour/labourParts'
import { useMonthlyCosts, useSaveMonthlyCosts } from './costingApi'

interface Entry {
  amount?: number
  note?: string
}

/** The costs that come as one amount a month. A head left empty isn't in the month's total. */
export default function MonthlyCostDrawer({ open, plantId, month, onClose }: {
  open: boolean
  plantId: number
  month: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const size = useDrawerSize(560)
  const rows = useMonthlyCosts(open ? { plantId, month } : null)
  const save = useSaveMonthlyCosts()
  const [entries, setEntries] = useState<Partial<Record<MonthlyCostHead, Entry>>>({})

  useEffect(() => {
    if (open) setEntries({})
  }, [open, month])

  const entry = (r: MonthlyCostRow): Entry => entries[r.head] ?? { amount: r.amount, note: r.note }
  const change = (r: MonthlyCostRow, patch: Entry) => setEntries((all) => ({ ...all, [r.head]: { ...entry(r), ...patch } }))
  const changed = (rows.data ?? []).filter((r) => {
    const e = entries[r.head]
    return e !== undefined && (e.amount !== r.amount || (e.note ?? '') !== (r.note ?? ''))
  })

  const onSave = async () => {
    try {
      await save.mutateAsync({
        plantId, month,
        lines: changed.map((r) => {
          const e = entry(r)
          return { head: r.head, amount: e.amount, note: e.note?.trim() || undefined }
        }),
      })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <Drawer open={open} onClose={onClose} size={size} destroyOnHidden
      title={t('costing.monthlyTitle', { month: dayjs(`${month}-01`).format('MMMM YYYY') })}
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} disabled={!changed.length} onClick={onSave}>{t('common.save')}</Button>
        </Space>
      }>
      <p className="cell-sub cost-monthly-hint">{t('costing.monthlyHint')}</p>
      <div className="cost-monthly">
        {(rows.data ?? []).map((r) => {
          const e = entry(r)
          return (
            <div key={r.head} className="cost-monthly-row">
              <div>
                <div className="cell-title">{t(`costing.heads.${r.head}`)}</div>
                <div className="cell-sub">{t(`costing.headHints.${r.head}`)}</div>
              </div>
              <InputNumber<number> min={0} precision={2} prefix="₹" inputMode="decimal" value={e.amount} style={{ width: '100%' }}
                aria-label={t(`costing.heads.${r.head}`)} onChange={(v) => change(r, { amount: v ?? undefined })} />
              {r.lastMonth != null && e.amount == null && (
                <Button type="link" size="small" className="cost-copy" onClick={() => change(r, { amount: r.lastMonth })}>
                  {t('costing.sameAsLast', { n: rupees(r.lastMonth) })}
                </Button>
              )}
              {e.amount != null && (
                <Input maxLength={255} value={e.note} placeholder={t('costing.notePlaceholder')} className="cost-note"
                  onChange={(ev) => change(r, { note: ev.target.value })} />
              )}
            </div>
          )
        })}
      </div>
    </Drawer>
  )
}
