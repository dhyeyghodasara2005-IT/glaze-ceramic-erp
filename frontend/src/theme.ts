import type { ThemeConfig } from 'antd'
import { COLORS } from './brand'

/**
 * Ant Design theme = the look of every button, input, table, etc.
 * "token" changes apply everywhere; "components" fine-tune one component type.
 * Full list of names: https://ant.design/docs/react/customize-theme
 */
export const theme: ThemeConfig = {
  token: {
    colorPrimary: COLORS.brand600,
    colorLink: COLORS.brand600,
    colorSuccess: '#15803D',
    colorWarning: '#B45309',
    colorError: '#DC2626',
    colorText: COLORS.ink,
    colorTextSecondary: COLORS.inkSoft,
    colorBorderSecondary: COLORS.line,
    colorBgLayout: COLORS.canvas,

    fontFamily:
      "'Plus Jakarta Sans Variable', 'Noto Sans Gujarati Variable', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
    fontSize: 14,

    borderRadius: 10,
    borderRadiusSM: 8,
    borderRadiusLG: 16,
    controlHeight: 40,
    controlHeightLG: 46,
    controlHeightSM: 30,

    boxShadow: '0 1px 2px rgba(31, 23, 20, 0.04), 0 8px 24px -12px rgba(31, 23, 20, 0.14)',
    boxShadowSecondary: '0 16px 40px -16px rgba(31, 23, 20, 0.3)',

    // Smooth "ease-out" curve used by antd's own animations (drawers, dropdowns).
    motionEaseOut: 'cubic-bezier(0.22, 1, 0.36, 1)',
    motionDurationMid: '0.22s',
  },
  components: {
    Button: {
      fontWeight: 600,
      primaryShadow: '0 8px 20px -8px rgba(168, 74, 35, 0.6)',
      defaultShadow: 'none',
      paddingInline: 18,
    },
    Card: {
      headerFontSize: 15,
    },
    Table: {
      headerBg: '#FBF8F5',
      headerColor: COLORS.inkSoft,
      headerSplitColor: 'transparent',
      rowHoverBg: '#FCF6F1',
      borderColor: '#F1EAE4',
      cellPaddingBlock: 14,
    },
    Segmented: {
      trackBg: '#EDE5DE',
      itemSelectedColor: COLORS.brand700,
    },
    Layout: {
      bodyBg: 'transparent',
      headerBg: 'transparent',
      siderBg: 'transparent',
    },
    Input: {
      activeShadow: '0 0 0 3px rgba(168, 74, 35, 0.14)',
    },
    Select: {
      optionSelectedBg: COLORS.brand50,
      activeOutlineColor: 'rgba(168, 74, 35, 0.14)',
    },
    Drawer: {
      footerPaddingBlock: 12,
    },
  },
}
