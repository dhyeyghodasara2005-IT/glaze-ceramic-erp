import { MACHINE_TYPES, type MachineLink, type MachineType } from '../../../api/types'

/**
 * Walking the plant's layout (the "feeds into" arrows from the Machines screen).
 * Plain functions, no React: the entry screens use them to fill in the obvious machine, and the Layout view uses
 * them to put each machine in its column.
 */

/** The little a walk needs to know about a machine. */
export interface LayoutMachine {
  id: number
  code: string
  type: MachineType
  active?: boolean
}

/** id -> the ids it points at, one way or the other. */
function arrows(links: MachineLink[], dir: 'down' | 'up'): Map<number, number[]> {
  const next = new Map<number, number[]>()
  for (const l of links) {
    const [from, to] = dir === 'down' ? [l.fromMachineId, l.toMachineId] : [l.toMachineId, l.fromMachineId]
    next.set(from, [...(next.get(from) ?? []), to])
  }
  return next
}

/**
 * The machines of one type you reach from a machine, following the arrows ('down': where its tiles go next) or
 * going against them ('up': where they came from). The walk passes through anything in between (a dryer, a
 * compensator) and stops at the first machine of that type on each path: from PRESS-1 down to KILN gives the kiln
 * after its line, not a kiln further on. Inactive machines are skipped.
 */
export function reachable(
  links: MachineLink[] | undefined,
  machines: LayoutMachine[] | undefined,
  fromId: number | undefined,
  type: MachineType,
  dir: 'down' | 'up',
): LayoutMachine[] {
  if (!links?.length || !machines || fromId === undefined) return []
  const byId = new Map(machines.map((m) => [m.id, m]))
  const next = arrows(links, dir)
  const found = new Map<number, LayoutMachine>()
  const seen = new Set<number>([fromId])
  const todo = [...(next.get(fromId) ?? [])]
  while (todo.length) {
    const id = todo.shift()!
    if (seen.has(id)) continue
    seen.add(id)
    const m = byId.get(id)
    if (m?.type === type) {
      if (m.active !== false) found.set(id, m)
      continue // stop here on this path
    }
    todo.push(...(next.get(id) ?? []))
  }
  return [...found.values()]
}

/**
 * The machine to fill in on an entry: the only one of that type the layout reaches from `fromId`; with no layout
 * (or nothing reached), the plant's only machine of that type. Several candidates = leave it to the supervisor.
 */
export function pickMachine(
  links: MachineLink[] | undefined,
  machines: LayoutMachine[] | undefined,
  fromId: number | undefined,
  type: MachineType,
  dir: 'down' | 'up',
): number | undefined {
  const near = reachable(links, machines, fromId, type, dir)
  if (near.length === 1) return near[0].id
  if (near.length > 1) return undefined
  const all = (machines ?? []).filter((m) => m.type === type && m.active !== false)
  return all.length === 1 ? all[0].id : undefined
}

/** Puts the machines the layout reaches first in a dropdown, keeping the rest in their order. */
export function nearestFirst<T extends { id: number }>(options: T[], near: LayoutMachine[]): T[] {
  const ids = new Set(near.map((m) => m.id))
  return [...options.filter((o) => ids.has(o.id)), ...options.filter((o) => !ids.has(o.id))]
}

/**
 * The Layout view's columns. A machine's column is the longest chain of arrows that leads to it, so every arrow
 * points to the right: PRESS-1 and PRESS-2 in column 0, DRY-1 in 1 ... Machines in no arrow at all are returned
 * apart, in process order.
 */
export function layoutColumns<T extends LayoutMachine>(machines: T[], links: MachineLink[]): { columns: T[][]; unlinked: T[] } {
  const linked = new Set(links.flatMap((l) => [l.fromMachineId, l.toMachineId]))
  const into = arrows(links, 'up')
  const depth = new Map<number, number>()
  // Longest path, worked out lazily; the server refuses loops, and `visiting` guards against a bad one anyway.
  const visiting = new Set<number>()
  const depthOf = (id: number): number => {
    const known = depth.get(id)
    if (known !== undefined) return known
    if (visiting.has(id)) return 0
    visiting.add(id)
    const d = Math.max(-1, ...(into.get(id) ?? []).map(depthOf)) + 1
    visiting.delete(id)
    depth.set(id, d)
    return d
  }
  const order = (a: T, b: T) => MACHINE_TYPES.indexOf(a.type) - MACHINE_TYPES.indexOf(b.type) || a.code.localeCompare(b.code)
  const columns: T[][] = []
  for (const m of machines.filter((x) => linked.has(x.id))) {
    const d = depthOf(m.id)
    columns[d] = [...(columns[d] ?? []), m]
  }
  return {
    columns: columns.filter(Boolean).map((c) => c.sort(order)),
    unlinked: machines.filter((m) => !linked.has(m.id)).sort(order),
  }
}
