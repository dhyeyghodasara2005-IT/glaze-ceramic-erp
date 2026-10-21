import { useMemo } from 'react'
import { Checkbox, Col, Row, Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import type { Permission, PermissionInfo } from '../../api/types'

interface Props {
  permissions: PermissionInfo[]
  /** Form.Item passes value + onChange automatically - this is how custom form inputs work in antd. */
  value?: Permission[]
  onChange?: (value: Permission[]) => void
  disabled?: boolean
}

/**
 * Checkboxes grouped by area (Organization, Users & Access, ...).
 * Each checkbox is controlled directly from `value`, so what you see is exactly what gets saved.
 */
export default function PermissionPicker({ permissions, value = [], onChange, disabled }: Props) {
  const { t } = useTranslation()

  // { "Organization": [...], "Users & Access": [...] } - keeps the server's order
  const groups = useMemo(() => {
    const map = new Map<string, PermissionInfo[]>()
    for (const p of permissions) map.set(p.group, [...(map.get(p.group) ?? []), p])
    return [...map.entries()]
  }, [permissions])

  const toggle = (code: Permission, checked: boolean) => {
    const next = checked ? [...value, code] : value.filter((v) => v !== code)
    onChange?.(next)
  }

  return (
    <div>
      {groups.map(([group, perms]) => (
        <div key={group} className="permission-group">
          <Typography.Text strong>{t(`permissionGroups.${group}`, { defaultValue: group })}</Typography.Text>
          <Row gutter={[8, 8]} style={{ marginTop: 8 }}>
            {perms.map((p) => (
              <Col xs={24} key={p.code}>
                <Checkbox
                  checked={value.includes(p.code)}
                  disabled={disabled}
                  onChange={(e) => toggle(p.code, e.target.checked)}
                >
                  {t(`permissions.${p.code}`, { defaultValue: p.description })}
                </Checkbox>
              </Col>
            ))}
          </Row>
        </div>
      ))}
    </div>
  )
}
