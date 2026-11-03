/* 统一 API 封装：与原生页面共用 localStorage 会话键，两套前端可互通登录态 */

const BASE = '/api'
const TOKEN_KEY = 'ctf_token'
const USER_KEY = 'ctf_user'

export class ApiError extends Error {
  constructor(code, message, status) {
    super(message)
    this.code = code
    this.status = status
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

export function setSession(data) {
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data.user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isStaff() {
  const user = getUser()
  return !!user && (user.role === 'ADMIN' || user.role === 'JUDGE')
}

/**
 * 统一请求：自动附带 Bearer、解包 ApiResponse{code,message,data}、401 跳登录。
 * @param {string} path 以 / 开头、不含 /api 前缀
 * @param {{method?:string, body?:any, authRedirect?:boolean}} options
 */
export async function api(path, options = {}) {
  const { method = 'GET', body, authRedirect = true } = options
  const headers = { Accept: 'application/json' }
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = 'Bearer ' + token

  let response
  try {
    response = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : (isForm ? body : JSON.stringify(body))
    })
  } catch (e) {
    throw new ApiError(5000, '网络请求失败，请检查服务是否已启动', 0)
  }

  let payload = null
  try {
    payload = await response.json()
  } catch (e) {
    /* 非 JSON 响应忽略 */
  }

  if (response.status === 401 && authRedirect) {
    clearSession()
    window.location.href = '/vue/player.html#/login'
    throw new ApiError(4010, '登录已过期', 401)
  }
  if (!response.ok || !payload || payload.code !== 0) {
    const code = payload && payload.code ? payload.code : response.status
    const message = payload && payload.message ? payload.message : '请求失败（HTTP ' + response.status + '）'
    throw new ApiError(code, message, response.status)
  }
  return payload.data
}

/** 附件下载：走带 Bearer 的请求，按 Content-Disposition 命名 */
export async function download(path, filename) {
  const token = getToken()
  const response = await fetch(BASE + path, {
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  })
  if (!response.ok) {
    let message = '附件下载失败'
    try {
      const p = await response.json()
      if (p && p.message) message = p.message
    } catch (e) {
      /* ignore */
    }
    throw new ApiError(response.status, message, response.status)
  }
  const blob = await response.blob()
  let name = filename || 'attachment'
  const cd = response.headers.get('Content-Disposition') || ''
  const utf8 = cd.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8) name = decodeURIComponent(utf8[1])
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
