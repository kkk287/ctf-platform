<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, setSession } from '../../shared/api.js'

const router = useRouter()
const mode = ref('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', displayName: '' })

const demoAccounts = [
  { label: '管理员 ADMIN', username: 'admin', password: 'Admin@123' },
  { label: '选手 alice · 0x00sec 队长', username: 'alice', password: 'Alice@123' },
  { label: '选手 bob · 0x00sec', username: 'bob', password: 'Bob@123' },
  { label: '选手 carol · Byte Raiders 队长', username: 'carol', password: 'Carol@123' }
]

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const formRef = ref(null)

function fill(item) {
  mode.value = 'login'
  form.username = item.username
  form.password = item.password
}

async function submit() {
  if (formRef.value) {
    const ok = await formRef.value.validate().catch(() => false)
    if (!ok) return
  }
  loading.value = true
  try {
    const payload = { username: form.username.trim(), password: form.password }
    if (mode.value === 'register' && form.displayName.trim()) {
      payload.displayName = form.displayName.trim()
    }
    const data = await api(mode.value === 'register' ? '/auth/register' : '/auth/login', {
      method: 'POST',
      body: payload,
      authRedirect: false
    })
    setSession(data)
    ElMessage.success(mode.value === 'register' ? '注册成功，已自动登录' : '登录成功')
    router.replace('/')
  } catch (err) {
    ElMessage.error(err.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div style="min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg-card)">
    <div style="width: 420px; background: #fff; border: 1px solid var(--border); border-radius: 12px; padding: 28px">
      <div class="brand" style="display: flex; align-items: center; gap: 8px; font-weight: 700; color: var(--text-title)">
        <span class="brand-mark">./ctf</span>
        <span>CTF PLATFORM <small class="muted">SEC LAB</small></span>
      </div>

      <el-tabs v-model="mode" style="margin-top: 18px">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <p v-if="mode === 'login'" class="muted small" style="margin: 0 0 14px">
        使用演示账号或注册新选手账号进入平台。
      </p>
      <p v-else class="muted small" style="margin: 0 0 14px">
        注册后自动登录，可创建或加入战队。
      </p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item v-if="mode === 'register'" label="昵称（可选）">
          <el-input v-model="form.displayName" maxlength="60" placeholder="显示在排行榜上的昵称" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="20" placeholder="3-20 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="mode === 'register' ? '密码长度需为 8-64 位' : '登录演示账号可直接使用下方凭据'"
          />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">
          {{ mode === 'register' ? '注册并进入' : '登录' }}
        </el-button>
      </el-form>

      <div v-if="mode === 'login'" style="margin-top: 18px">
        <div class="muted small" style="margin-bottom: 8px">演示账号（点击自动填充）</div>
        <el-button
          v-for="item in demoAccounts"
          :key="item.username"
          size="small"
          style="width: 100%; margin-bottom: 6px; justify-content: space-between"
          @click="fill(item)"
        >
          <span>{{ item.label }}</span>
          <span class="mono muted">{{ item.username }} / {{ item.password }}</span>
        </el-button>
      </div>
    </div>
  </div>
</template>
