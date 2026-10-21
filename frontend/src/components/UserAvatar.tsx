// Round avatar with the person's initials. The colour is picked from their name,
// so the same person always gets the same colour.

const GRADIENTS = [
  ['#D9773F', '#8A3B1C'], // terracotta
  ['#C08A3E', '#7A5419'], // ochre
  ['#4F8A8B', '#285A5B'], // glaze teal
  ['#7D6BA6', '#4B3D73'], // plum
  ['#5E8C61', '#33573A'], // sage
  ['#B5636A', '#7A3239'], // brick rose
]

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0][0] ?? ''
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? '') : ''
  return (first + last).toUpperCase()
}

function pick(name: string) {
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return GRADIENTS[hash % GRADIENTS.length]
}

export default function UserAvatar({ name, size = 32 }: { name: string; size?: number }) {
  const [from, to] = pick(name)
  return (
    <span
      className="user-avatar"
      aria-hidden
      style={{
        width: size,
        height: size,
        fontSize: Math.round(size * 0.38),
        background: `linear-gradient(135deg, ${from}, ${to})`,
      }}
    >
      {initials(name)}
    </span>
  )
}
