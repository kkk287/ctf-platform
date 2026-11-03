/* 展示层格式化与 Element Plus 语义映射 */

export function formatDate(value, withSeconds = false) {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return (
    d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
    pad(d.getHours()) + ':' + pad(d.getMinutes()) + (withSeconds ? ':' + pad(d.getSeconds()) : '')
  )
}

export function formatShort(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

export function relativeTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return String(value)
  const diff = Math.floor((Date.now() - d.getTime()) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  if (diff < 2592000) return Math.floor(diff / 86400) + ' 天前'
  return formatShort(value)
}

export function countdown(endAt) {
  if (!endAt) return ''
  const diff = new Date(endAt).getTime() - Date.now()
  if (diff <= 0) return '已结束'
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  const mins = Math.floor((diff % 3600000) / 60000)
  if (days > 0) return days + ' 天 ' + hours + ' 小时'
  if (hours > 0) return hours + ' 小时 ' + mins + ' 分钟'
  return mins + ' 分钟'
}

export function sizeText(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

export function eventStatusType(status) {
  if (status === 'ONGOING') return 'success'
  if (status === 'ENDED') return 'info'
  return 'warning'
}

export function eventTypeType(type) {
  return type === 'PRACTICE' ? 'success' : 'primary'
}

export function difficultyType(difficulty) {
  if (difficulty === 'EASY') return 'success'
  if (difficulty === 'HARD') return 'danger'
  return 'warning'
}

export const CATEGORIES = [
  ['WEB', 'Web'],
  ['PWN', 'Pwn'],
  ['CRYPTO', 'Crypto'],
  ['MISC', 'Misc'],
  ['REVERSE', 'Reverse']
]

export const DIFFICULTIES = [
  ['EASY', '简单'],
  ['MEDIUM', '中等'],
  ['HARD', '困难']
]

export const ROLES = [
  ['USER', '选手'],
  ['JUDGE', '裁判'],
  ['TEACHER', '教师'],
  ['OBSERVER', '观摩'],
  ['ADMIN', '管理员']
]

export const AUDIT_ACTIONS = [
  ['USER_BAN', '用户封禁'],
  ['USER_UNBAN', '用户解封'],
  ['USER_ROLE_CHANGE', '角色调整'],
  ['EVENT_CREATE', '赛事创建'],
  ['EVENT_UPDATE', '赛事更新'],
  ['EVENT_DELETE', '赛事删除'],
  ['CHALLENGE_CREATE', '题目创建'],
  ['CHALLENGE_UPDATE', '题目更新'],
  ['CHALLENGE_DELETE', '题目删除'],
  ['HINT_CREATE', '提示创建'],
  ['HINT_UPDATE', '提示更新'],
  ['HINT_DELETE', '提示删除'],
  ['ANNOUNCEMENT_CREATE', '公告发布'],
  ['ANNOUNCEMENT_UPDATE', '公告更新'],
  ['ANNOUNCEMENT_DELETE', '公告删除']
]

export function auditActionLabel(action) {
  const hit = AUDIT_ACTIONS.find(([v]) => v === action)
  return hit ? hit[1] : action
}

export const ROLE_LABEL = { ADMIN: '管理员', JUDGE: '裁判', TEACHER: '教师', OBSERVER: '观摩', USER: '选手' }
