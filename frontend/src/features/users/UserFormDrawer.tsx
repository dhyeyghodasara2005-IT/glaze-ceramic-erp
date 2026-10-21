import { useEffect } from 'react'
import { App, Button, Col, Drawer, Form, Input, Row, Select, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { User, UserCreateRequest } from '../../api/types'
import { blankToUndefined, PASSWORD_MAX, PASSWORD_MIN, patterns } from '../../api/validation'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useAuth } from '../../auth/AuthContext'
import { LANGUAGES } from '../../i18n'
import { usePlants } from '../plants/api'
import { useRoles } from '../roles/api'
import { useCreateUser, useUpdateUser } from './api'

interface Props {
  open: boolean
  user?: User
  onClose: () => void
}

// One form type for both create and edit; username/password are only sent on create.
type FormValues = UserCreateRequest

export default function UserFormDrawer({ open, user, onClose }: Props) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can, refresh, user: me } = useAuth()
  const drawerSize = useDrawerSize()
  const [form] = Form.useForm<FormValues>()
  const create = useCreateUser()
  const update = useUpdateUser()
  const roles = useRoles(open && can('ROLE_VIEW'))
  const plants = usePlants(undefined, open && can('PLANT_VIEW'))
  const allPlants = Form.useWatch('allPlantsAccess', form)
  const isNew = !user

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(
      user
        ? {
            ...user,
            roleIds: user.roles.map((r) => r.id),
            plantIds: user.plants.map((p) => p.id),
          }
        : { allPlantsAccess: false, preferredLanguage: 'en', roleIds: [], plantIds: [] },
    )
  }, [open, user, form])

  const onFinish = async (raw: FormValues) => {
    const values = blankToUndefined(raw)
    const body = {
      fullName: values.fullName,
      phone: values.phone,
      email: values.email,
      roleIds: values.roleIds,
      plantIds: values.allPlantsAccess ? [] : (values.plantIds ?? []),
      allPlantsAccess: values.allPlantsAccess ?? false,
      preferredLanguage: values.preferredLanguage,
    }
    try {
      if (isNew) {
        await create.mutateAsync({ ...body, username: values.username, password: values.password })
      } else {
        await update.mutateAsync({ id: user.id, body })
        if (user.id === me?.id) await refresh() // edited yourself: reload your own menus/plants
      }
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={isNew ? t('user.new') : t('user.edit')}
      open={open}
      onClose={onClose}
      size={drawerSize}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={create.isPending || update.isPending} onClick={() => form.submit()}>
            {t('common.save')}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="username"
              label={t('user.username')}
              tooltip={t('user.usernameHint')}
              normalize={(v?: string) => v?.toLowerCase()}
              rules={isNew ? [required, { pattern: patterns.username, message: t('user.invalidUsername') }] : []}
            >
              <Input disabled={!isNew} autoCapitalize="none" autoComplete="off" maxLength={50} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="fullName" label={t('user.fullName')} rules={[required]}>
              <Input maxLength={150} />
            </Form.Item>
          </Col>
        </Row>

        {isNew && (
          <Form.Item
            name="password"
            label={t('user.password')}
            extra={t('user.passwordHint')}
            rules={[required, { min: PASSWORD_MIN, max: PASSWORD_MAX, message: t('auth.passwordLength') }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        )}

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="phone" label={t('user.phone')} rules={[{ pattern: patterns.phone, message: t('company.invalidPhone') }]}>
              <Input inputMode="tel" maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="email" label={t('user.email')} rules={[{ type: 'email', message: t('company.invalidEmail') }]}>
              <Input inputMode="email" maxLength={150} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="roleIds" label={t('user.roles')} rules={[{ required: true, message: t('user.selectRole') }]}>
          <Select
            mode="multiple"
            loading={roles.isLoading}
            showSearch={{ optionFilterProp: 'label' }}
            options={roles.data?.map((r) => ({ value: r.id, label: r.name }))}
          />
        </Form.Item>

        <Form.Item name="allPlantsAccess" label={t('user.allPlantsAccess')} extra={t('user.allPlantsHint')} valuePropName="checked">
          <Switch />
        </Form.Item>

        {!allPlants && (
          <Form.Item name="plantIds" label={t('user.plants')}>
            <Select
              mode="multiple"
              loading={plants.isLoading}
              showSearch={{ optionFilterProp: 'label' }}
              options={plants.data?.map((p) => ({ value: p.id, label: `${p.code} - ${p.name}` }))}
            />
          </Form.Item>
        )}

        <Form.Item name="preferredLanguage" label={t('user.language')}>
          <Select options={LANGUAGES.map((l) => ({ value: l.value, label: l.label }))} style={{ maxWidth: 200 }} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
