/**
 * 实时推送客户端：原生 WebSocket + token 鉴权 + 主题订阅 + 自动重连。
 * 页面按需订阅主题，业务侧仍保留 30s 轮询作为兜底。
 *
 * 主题约定：
 *   scoreboard:<eventId>  榜单变更（只作刷新信号，不携带明细）
 *   solves:<eventId>      解题动态（黑灯模式下服务端不推送该主题）
 *   announcement          公告增删改
 */
import { getToken } from './api.js'

const listeners = new Map()
const statusListeners = new Set()
let socket = null
let reconnectTimer = null
let reconnectAttempts = 0
let pingTimer = null
let manualClose = false

function wsUrl() {
  const token = getToken()
  if (!token) return null
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return protocol + '//' + location.host + '/ws?token=' + encodeURIComponent(token)
}

function notifyStatus(status) {
  statusListeners.forEach((fn) => {
    try {
      fn(status)
    } catch (e) {
      /* 忽略监听异常 */
    }
  })
}

function sendSubscribe(topics) {
  if (socket && socket.readyState === WebSocket.OPEN && topics.length) {
    socket.send(JSON.stringify({ action: 'subscribe', topics }))
  }
}

function handleMessage(raw) {
  let msg
  try {
    msg = JSON.parse(raw)
  } catch (e) {
    return
  }
  if (msg.type !== 'event' || !msg.topic) return
  const matched = listeners.get(msg.topic)
  if (matched) {
    matched.forEach((fn) => {
      try {
        fn(msg)
      } catch (e) {
        /* 单个监听异常不影响其他订阅 */
      }
    })
  }
}

function scheduleReconnect() {
  if (reconnectTimer || manualClose) return
  const delay = Math.min(15000, 1000 * Math.pow(2, reconnectAttempts))
  reconnectAttempts += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    if (listeners.size) open()
  }, delay)
}

function open() {
  if (manualClose || !getToken()) return
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return
  const url = wsUrl()
  if (!url) return
  try {
    socket = new WebSocket(url)
  } catch (e) {
    scheduleReconnect()
    return
  }
  socket.onopen = () => {
    reconnectAttempts = 0
    notifyStatus('connected')
    sendSubscribe(Array.from(listeners.keys()))
    if (pingTimer) clearInterval(pingTimer)
    pingTimer = setInterval(() => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ action: 'ping' }))
      }
    }, 25000)
  }
  socket.onmessage = (event) => handleMessage(event.data)
  socket.onclose = () => {
    notifyStatus('disconnected')
    if (pingTimer) {
      clearInterval(pingTimer)
      pingTimer = null
    }
    socket = null
    if (!manualClose) scheduleReconnect()
  }
  socket.onerror = () => {
    try {
      socket && socket.close()
    } catch (e) {
      /* 关闭异常忽略 */
    }
  }
}

function closeSocket() {
  manualClose = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (pingTimer) {
    clearInterval(pingTimer)
    pingTimer = null
  }
  if (socket) {
    try {
      socket.close()
    } catch (e) {
      /* 忽略 */
    }
    socket = null
  }
  reconnectAttempts = 0
}

/**
 * 订阅主题，返回取消订阅函数。最后一个订阅取消后自动断开连接。
 */
export function subscribe(topic, handler) {
  if (!listeners.has(topic)) listeners.set(topic, new Set())
  listeners.get(topic).add(handler)
  manualClose = false
  if (!socket || socket.readyState === WebSocket.CLOSED || socket.readyState === WebSocket.CLOSING) {
    open()
  } else if (socket.readyState === WebSocket.OPEN) {
    sendSubscribe([topic])
  }
  return () => {
    const set = listeners.get(topic)
    if (set) {
      set.delete(handler)
      if (!set.size) listeners.delete(topic)
    }
    if (!listeners.size) closeSocket()
  }
}

/** 连接状态订阅：connected / disconnected */
export function onRealtimeStatus(handler) {
  statusListeners.add(handler)
  handler(socket && socket.readyState === WebSocket.OPEN ? 'connected' : 'disconnected')
  return () => statusListeners.delete(handler)
}

/** 手动重建连接（如切换账号后） */
export function reconnectRealtime() {
  closeSocket()
  manualClose = false
  if (listeners.size) open()
}
