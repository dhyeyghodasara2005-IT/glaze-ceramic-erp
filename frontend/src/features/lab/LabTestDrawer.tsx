import { useEffect, useState } from 'react'
import { App, Button, DatePicker, Descriptions, Drawer, Input, InputNumber, Popconfirm, Space, Spin } from 'antd'
import { CheckCircleFilled, CheckOutlined, CloseCircleFilled, CloseOutlined, ExperimentOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { errorMessage } from '../../api/errors'
import type { LabVerdict } from '../../api/types'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useAuth } from '../../auth/AuthContext'
import { shortDate } from '../store/format'
import { fmt } from '../dashboard/charts'
import { useDeleteLabTest, useLabForm, useSaveLabTest } from './labApi'
import { VerdictTag, rangeText, within } from './labParts'

/**
 * One sample (a material from one truck): the lab types what it found and accepts or rejects it. While the GRN is a
 * draft the result may be corrected or taken back; once the GRN is posted it stays as it is.
 */
export default function LabTestDrawer({ sample, onClose }: {
  sample?: { documentId: number; materialId: number }
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can } = useAuth()
  const size = useDrawerSize(620)
  const form = useLabForm(sample)
  const save = useSaveLabTest()
  const remove = useDeleteLabTest()
  const f = form.data
  const test = f?.test
  const [values, setValues] = useState<Record<number, number | undefined>>({})
  const [testedOn, setTestedOn] = useState<Dayjs>(dayjs())
  const [testedBy, setTestedBy] = useState<string>()
  const [remarks, setRemarks] = useState<string>()

  // the saved result fills the form; a new sample starts empty, today
  const key = f ? `${f.sample.documentId}/${f.sample.materialId}/${test?.updatedAt ?? ''}` : ''
  useEffect(() => {
    if (!f) return
    setValues(Object.fromEntries((f.test?.results ?? []).map((r) => [r.parameterId, r.value])))
    setTestedOn(f.test ? dayjs(f.test.testedOn) : dayjs())
    setTestedBy(f.test?.testedBy)
    setRemarks(f.test?.remarks)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  const mayEnter = can('LAB_ENTRY') && (!test || test.editable) && f?.sample.status === 'DRAFT'
  const entered = (f?.parameters ?? []).filter((p) => values[p.id] != null)
  const outside = entered.filter((p) => !within(values[p.id]!, p.minValue, p.maxValue))

  const onSave = async (verdict: LabVerdict) => {
    if (!f) return
    try {
      await save.mutateAsync({
        documentId: f.sample.documentId, materialId: f.sample.materialId, testedOn: testedOn.format('YYYY-MM-DD'),
        testedBy: testedBy?.trim() || undefined, verdict, remarks: remarks?.trim() || undefined,
        results: entered.map((p) => ({ parameterId: p.id, value: values[p.id]! })),
      })
      message.success(t(verdict === 'ACCEPTED' ? 'lab.acceptedOk' : 'lab.rejectedOk', { what: f.sample.materialName }))
      onClose()
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onTakeBack = async () => {
    if (!test) return
    try {
      await remove.mutateAsync(test.id)
      message.success(t('lab.takenBack'))
      onClose()
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const s = f?.sample
  return (
    <Drawer open={sample !== undefined} onClose={onClose} size={size} destroyOnHidden
      title={s ? <span><ExperimentOutlined /> {s.materialName}</span> : t('lab.test')}
      extra={test && <Space><span className="code-badge">{test.testNo}</span><VerdictTag verdict={test.verdict} /></Space>}>
      {!f ? <Spin /> : (
        <>
          <Descriptions size="small" column={{ xs: 1, sm: 2 }} className="lab-sample">
            <Descriptions.Item label={t('lab.supplier')}>{s!.supplierName ?? '–'}</Descriptions.Item>
            <Descriptions.Item label={t('lab.vehicle')}>{s!.vehicleNo ?? '–'}</Descriptions.Item>
            <Descriptions.Item label={t('lab.received')}>{fmt(s!.receivedQty, 3)} {s!.unit}</Descriptions.Item>
            <Descriptions.Item label={t('lab.grn')}>
              {s!.docNo ?? t('lab.grnDraft', { date: shortDate(s!.docDate) })}
            </Descriptions.Item>
          </Descriptions>

          {f.parameters.length === 0 && !test && <p className="cell-sub">{t('lab.noTestsSetUp')}</p>}

          <div className="lab-results">
            {(mayEnter ? f.parameters.map((p) => ({ id: p.id, name: p.name, unit: p.unit, minValue: p.minValue, maxValue: p.maxValue, value: values[p.id] }))
              : (test?.results ?? []).map((r) => ({ id: r.parameterId, name: r.name, unit: r.unit, minValue: r.minValue, maxValue: r.maxValue, value: r.value })))
              .map((p) => {
                const range = rangeText(p.minValue, p.maxValue, p.unit)
                const ok = p.value == null ? undefined : within(p.value, p.minValue, p.maxValue)
                return (
                  <div key={p.id} className={`lab-result${ok === false ? ' bad' : ok ? ' ok' : ''}`}>
                    <div>
                      <div className="cell-title">{p.name}</div>
                      <div className="cell-sub">{range ? t('lab.range', { range }) : t('lab.noRange')}</div>
                    </div>
                    {mayEnter ? (
                      <InputNumber<number> value={p.value} suffix={p.unit} inputMode="decimal" className="lab-value"
                        onChange={(v) => setValues((all) => ({ ...all, [p.id]: v ?? undefined }))} />
                    ) : <b className="lab-value">{p.value == null ? '–' : `${fmt(p.value, 3)} ${p.unit ?? ''}`}</b>}
                    <span className="lab-mark">
                      {ok === true && <CheckCircleFilled />}
                      {ok === false && <><CloseCircleFilled /> {t('lab.outside')}</>}
                    </span>
                  </div>
                )
              })}
          </div>

          {mayEnter ? (
            <>
              <div className="lab-meta">
                <DatePicker value={testedOn} format="DD MMM YYYY" allowClear={false} onChange={(d) => d && setTestedOn(d)}
                  disabledDate={(d) => d.isAfter(dayjs(), 'day') || d.isBefore(dayjs(s!.docDate), 'day')} />
                <Input value={testedBy} maxLength={150} placeholder={t('lab.testedByPlaceholder')} onChange={(e) => setTestedBy(e.target.value)} />
              </div>
              <Input.TextArea value={remarks} maxLength={500} autoSize={{ minRows: 2, maxRows: 5 }} placeholder={t('lab.remarksPlaceholder')}
                onChange={(e) => setRemarks(e.target.value)} />
              <div className="lab-decide">
                <div className={outside.length ? 'lab-summary bad' : 'lab-summary'}>
                  {entered.length === 0 ? t('lab.enterSome')
                    : outside.length ? t('lab.outsideN', { count: outside.length, names: outside.map((p) => p.name).join(', ') })
                      : t('lab.allWithin', { count: entered.length })}
                </div>
                <Space wrap>
                  <Button danger icon={<CloseOutlined />} disabled={!entered.length} loading={save.isPending} onClick={() => onSave('REJECTED')}>
                    {t('lab.reject')}
                  </Button>
                  <Button type="primary" icon={<CheckOutlined />} disabled={!entered.length} loading={save.isPending} onClick={() => onSave('ACCEPTED')}>
                    {t('lab.accept')}
                  </Button>
                </Space>
                <div className="cell-sub">{t('lab.decideHint')}</div>
              </div>
              {test && (
                <Popconfirm title={t('lab.takeBackConfirm')} okText={t('lab.takeBack')} cancelText={t('common.cancel')} onConfirm={onTakeBack}>
                  <Button type="link" danger className="lab-take-back">{t('lab.takeBack')}</Button>
                </Popconfirm>
              )}
            </>
          ) : test ? (
            <Descriptions size="small" column={1} className="lab-sample">
              <Descriptions.Item label={t('lab.testedOn')}>{shortDate(test.testedOn)}{test.testedBy ? ` · ${test.testedBy}` : ''}</Descriptions.Item>
              {test.remarks && <Descriptions.Item label={t('lab.remarks')}>{test.remarks}</Descriptions.Item>}
              <Descriptions.Item label={t('lab.what')}>
                {test.verdict === 'REJECTED' ? t('lab.rejectedMeans') : t('lab.acceptedMeans')}
                {!test.editable && ` ${t('lab.grnPosted')}`}
              </Descriptions.Item>
            </Descriptions>
          ) : <p className="cell-sub">{t('lab.waitingForLab')}</p>}
        </>
      )}
    </Drawer>
  )
}
