import { Grid } from 'antd'

/** Drawer width: fixed on tablets/PCs, full screen on phones (narrower than 576px). */
export function useDrawerSize(desktopWidth = 560): number | string {
  const screens = Grid.useBreakpoint()
  return screens.sm ? desktopWidth : '100%'
}
