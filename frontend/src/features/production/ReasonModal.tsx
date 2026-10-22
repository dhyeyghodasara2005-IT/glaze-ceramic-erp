import { Form, Input, Modal } from 'antd'
import { useTranslation } from 'react-i18next'

/** "Cancel X?" with a required reason - used for batches and runs. */
export default function ReasonModal({ open, title, hint, okText, loading, onOk, onClose }: {
  open: boolean
  title: string
  hint: string
  okText: string
  loading: boolean
  onOk: (reason: string) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [form] = Form.useForm<{ reason: string }>()
  return (
    <Modal
      open={open}
      title={title}
      okText={okText}
      okButtonProps={{ danger: true }}
      cancelText={t('store.keep')}
      confirmLoading={loading}
      onOk={() => form.submit()}
      onCancel={onClose}
      destroyOnHidden
    >
      <p className="panel-hint" style={{ margin: '0 0 12px' }}>{hint}</p>
      <Form form={form} layout="vertical" onFinish={(v) => onOk(v.reason)}>
        <Form.Item name="reason" label={t('store.cancelReason')} rules={[{ required: true, whitespace: true, message: t('common.required') }]}>
          <Input maxLength={255} autoFocus />
        </Form.Item>
      </Form>
    </Modal>
  )
}
