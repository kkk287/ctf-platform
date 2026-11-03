import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, getUser } from '../shared/api.js'
import Login from './views/Login.vue'
import ChallengeList from './views/ChallengeList.vue'
import ChallengeDetail from './views/ChallengeDetail.vue'
import Scoreboard from './views/Scoreboard.vue'
import Profile from './views/Profile.vue'
import TheoryExam from './views/TheoryExam.vue'
import WriteupList from './views/WriteupList.vue'
import AwdBoard from './views/AwdBoard.vue'

const routes = [
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/', component: ChallengeList },
  { path: '/challenge/:id', component: ChallengeDetail },
  { path: '/scoreboard', component: Scoreboard },
  { path: '/theory', component: TheoryExam },
  { path: '/writeups', component: WriteupList },
  { path: '/awd', component: AwdBoard },
  { path: '/profile', component: Profile }
]

const router = createRouter({
  // hash 模式下不传 base：默认以当前文档为基准，链接形如 /vue/player.html#/xxx
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const authed = !!getToken() && !!getUser()
  if (!to.meta.public && !authed) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && authed) {
    return { path: '/' }
  }
  return true
})

export default router
