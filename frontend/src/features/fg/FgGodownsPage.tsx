import { useState } from 'react'
import { Button } from 'antd'
import { PlusOutlined, TableOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import NoPlant from '../store/NoPlant'
import { useFgGodowns } from './fgApi'
import FgGodownDrawer from './FgGodownDrawer'

/** The plant's finished-goods godowns. Each card opens the godown's pillar map. */
export default function FgGodownsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data } = useFgGodowns(currentPlant?.id)
  const [creating, setCreating] = useState(false)

  if (!currentPlant) return <NoPlant />

  const newButton = can('FG_ENTRY') && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>{t('fg.newGodown')}</Button>
  )

  return (
    <>
      <PageHeader title={t('fg.godownsTitle')} subtitle={t('fg.godownsSubtitle')} icon={<TableOutlined />} extra={newButton} />
      {data && data.length === 0 && (
        <FadeIn className="panel"><EmptyState title={t('fg.noGodowns')} hint={t('fg.noGodownsHint')} action={newButton} /></FadeIn>
      )}
      <Stagger className="godown-grid">
        {data?.map((g) => (
          <StaggerItem key={g.id}>
            <button type="button" className={`godown-card${g.active ? '' : ' inactive'}`} onClick={() => navigate(`/fg/godowns/${g.id}`)}>
              <span className="code-badge">{g.code}</span>
              <span className="cell-title">{g.name}</span>
              <span className="cell-sub">
                {t('fg.pillarsN', { count: g.pillars })} · {t('finishing.boxesN', { n: g.boxes.toLocaleString('en-IN') })}
                {!g.active && ` · ${t('common.inactive')}`}
              </span>
            </button>
          </StaggerItem>
        ))}
      </Stagger>
      <FgGodownDrawer open={creating} plantId={currentPlant.id} onClose={() => setCreating(false)}
        onSaved={(g) => navigate(`/fg/godowns/${g.id}`)} />
    </>
  )
}
