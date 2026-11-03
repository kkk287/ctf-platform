import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, getUser } from '../shared/api.js'
import ChallengeAdmin from './views/ChallengeAdmin.vue'
import EventAdmin from './views/EventAdmin.vue'
import UserAdmin from './views/UserAdmin.vue'
import SubmissionAdmin from './views/SubmissionAdmin.vue'
import AuditAdmin from './views/AuditAdmin.vue'
import AnomalyAdmin from './views/AnomalyAdmin.vue'
import AnnouncementAdmin from './views/AnnouncementAdmin.vue'
import TheoryAdmin from './views/TheoryAdmin.vue'
import WriteupAdmin from './views/WriteupAdmin.vue'
import InstanceAdmin from './views/InstanceAdmin.vue'
import AwdAdmin from './views/AwdAdmin.vue'

const routes = [
  { path: '/', redirect: '/challenges' },
  { path: '/challenges', component: ChallengeAdmin, meta: { title: '题目管理' } },
  { path: '/events', component: EventAdmin, meta: { title: '赛事/练习区' } },
  { path: '/theory', component: TheoryAdmin, meta: { title: '理论赛管理' } },
  { path: '/writeups', component: WriteupAdmin, meta: { title: 'Writeup 审核' } },
  { path: '/instances', component: InstanceAdmin, meta: { title: '靶机实例总览' } },
  { path: '/awd', component: AwdAdmin, meta: { title: 'AWD 轮次管理' } },
  { path: '/users', component: UserAdmin, meta: { title: '用户与角色' } },
  { path: '/submissions', component: SubmissionAdmin, meta: { title: '提交审计' } },
  { path: '/anomalies', component: AnomalyAdmin, meta: { title: '异常告警' } },
  { path: '/announcements', component: AnnouncementAdmin, meta: { title: '公告管理' } },
  { path: '/audit', component: AuditAdmin, meta: { title: '审计日志' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(() => {
  if (!getToken() || !getUser()) {
    window.location.href = '/vue/player.html#/login'
    return false
  }
  return true
})

export default router
