import { DatabaseOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import NoPlant from '../store/NoPlant'
import { useSilos } from './pressApi'
import SiloCards from './SiloCards'

/** Every silo with the spray runs whose powder is still in it, oldest first. */
export default function SilosPage() {
  const { t } = useTranslation()
  const { currentPlant } = useAuth()
  const { data } = useSilos(currentPlant?.id)
  if (!currentPlant) return <NoPlant />
  return (
    <>
      <PageHeader title={t('press.silosTitle')} subtitle={t('press.silosSubtitle')} icon={<DatabaseOutlined />} />
      <FadeIn delay={0.08}>
        <SiloCards silos={data} detailed />
      </FadeIn>
    </>
  )
}
