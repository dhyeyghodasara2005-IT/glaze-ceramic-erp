import { Select, Tag } from 'antd'
import { ShopOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'

/**
 * Picks which plant you are working in. Production, stock and dispatch screens (later modules)
 * will show and save data for this plant.
 */
export default function PlantSwitcher() {
  const { t } = useTranslation()
  const { user, currentPlant, setCurrentPlantId } = useAuth()
  if (!user) return null

  if (user.plants.length === 0) {
    return (
      <Tag icon={<ShopOutlined />} style={{ borderRadius: 999, padding: '4px 12px', margin: 0 }}>
        {t('common.noPlant')}
      </Tag>
    )
  }
  return (
    <Select
      variant="filled"
      value={currentPlant?.id}
      onChange={setCurrentPlantId}
      prefix={<ShopOutlined style={{ color: 'var(--brand-600)' }} />}
      popupMatchSelectWidth={false}
      style={{ minWidth: 150, maxWidth: 240 }}
      options={user.plants.map((p) => ({ value: p.id, label: p.name }))}
      aria-label={t('common.plant')}
    />
  )
}
