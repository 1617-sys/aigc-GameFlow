<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api, session } from './api'
import AssetGallery from './components/AssetGallery.vue'
import ProviderSelector from './components/ProviderSelector.vue'
import SystemOverview from './components/SystemOverview.vue'
import TaskDetailDrawer from './components/TaskDetailDrawer.vue'
import TaskList from './components/TaskList.vue'

const navItems = [
  { id: 'overview', mark: '01', label: '概览', copy: '链路与任务摘要' },
  { id: 'create', mark: '02', label: '创建任务', copy: '提交图片生成' },
  { id: 'jobs', mark: '03', label: '任务中心', copy: '状态、重试与事件' },
  { id: 'assets', mark: '04', label: '素材库', copy: 'MinIO 生成结果' },
  { id: 'system', mark: '05', label: '系统状态', copy: 'Provider 与基础设施' }
]

const pageMeta = {
  overview: ['ASYNC IMAGE WORKSPACE', '异步生成控制台', '从任务接收到图片入库，观察每一步如何可靠执行。'],
  create: ['NEW GENERATION JOB', '创建生成任务', '选择可用的图片服务，提交后立即返回任务编号。'],
  jobs: ['JOB OPERATIONS', '任务中心', '筛选任务、处理失败，并查看 Outbox、队列和 Worker 事件。'],
  assets: ['ASSET LIBRARY', '生成素材库', '浏览已经保存到 MinIO 的生成结果。'],
  system: ['SYSTEM OBSERVABILITY', '系统状态', '确认 Provider 配置与异步链路组件的可观测范围。']
}

const loggedIn = ref(Boolean(session.token()))
const authMode = ref('login')
const auth = reactive({ username: '', password: '' })
const user = reactive({ userId: null, balance: null })
const form = reactive({ prompt: '', negativePrompt: '', preferredProvider: 'WANX', size: '1024x1024', quality: '' })
const currentView = ref('overview')
const tasks = ref([])
const providers = ref([])
const images = reactive({})
const events = ref([])
const selectedTask = ref(null)
const detailLoading = ref(false)
const loading = ref(false)
const refreshing = ref(false)
const lastUpdated = ref('')
const message = ref('')
const error = ref('')
let pollTimer

const activeCount = computed(() => tasks.value.filter(task => [0, 1, 5].includes(task.status)).length)
const queuedCount = computed(() => tasks.value.filter(task => task.status === 0).length)
const failedCount = computed(() => tasks.value.filter(task => task.status === 3).length)
const successCount = computed(() => tasks.value.filter(task => task.status === 2).length)
const successRate = computed(() => {
  const finished = successCount.value + failedCount.value
  return finished ? Math.round(successCount.value / finished * 100) : 0
})
const averageLatency = computed(() => {
  const values = tasks.value.map(task => task.latencyMs).filter(value => Number.isFinite(value))
  if (!values.length) return '—'
  return `${(values.reduce((sum, value) => sum + value, 0) / values.length / 1000).toFixed(1)}s`
})
const currentPage = computed(() => pageMeta[currentView.value])

function notify(text, isError = false) {
  message.value = isError ? '' : text
  error.value = isError ? text : ''
  window.setTimeout(() => {
    if (message.value === text) message.value = ''
    if (error.value === text) error.value = ''
  }, 3600)
}

async function authenticate() {
  if (!auth.username.trim() || !auth.password) return notify('请输入用户名和密码', true)
  loading.value = true
  try {
    if (authMode.value === 'register') {
      await api.register(auth.username.trim(), auth.password)
      notify('注册成功，请登录')
      authMode.value = 'login'
      return
    }
    const result = await api.login(auth.username.trim(), auth.password)
    session.save(result.token)
    user.userId = result.userId
    user.balance = result.balance
    loggedIn.value = true
    await loadDashboard()
  } catch (e) {
    notify(e.message, true)
  } finally {
    loading.value = false
  }
}

async function loadDashboard(silent = false) {
  if (!silent) refreshing.value = true
  try {
    const [taskData, providerData] = await Promise.all([api.tasks(), api.providers()])
    tasks.value = taskData || []
    providers.value = providerData || []
    if (!providers.value.includes(form.preferredProvider)) form.preferredProvider = providers.value[0] || ''
    lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
    await loadFinishedImages()
  } catch (e) {
    if (!session.token()) logout()
    if (!silent) notify(e.message, true)
  } finally {
    refreshing.value = false
  }
}

async function loadFinishedImages() {
  const pending = tasks.value.filter(task => task.status === 2 && !images[task.taskUuid])
  await Promise.allSettled(pending.map(async task => { images[task.taskUuid] = await api.image(task.taskUuid) }))
}

async function refreshActiveTasks() {
  const activeTasks = tasks.value.filter(task => [0, 1, 5].includes(task.status))
  if (!activeTasks.length) return
  try {
    const updates = await api.taskStatuses(activeTasks.map(task => task.taskUuid))
    const updateMap = new Map(updates.map(task => [task.taskUuid, task]))
    tasks.value = tasks.value.map(task => updateMap.get(task.taskUuid) || task)
    lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
    await loadFinishedImages()
  } catch {
    if (!session.token()) logout()
  }
}

async function submitTask() {
  if (!form.prompt.trim()) return notify('请先填写画面描述', true)
  if (!form.preferredProvider) return notify('当前没有可用的图片生成服务，请检查后端配置', true)
  loading.value = true
  try {
    const result = await api.submit({
      prompt: form.prompt.trim(), negativePrompt: form.negativePrompt.trim() || null,
      preferredProvider: form.preferredProvider, size: form.size, quality: form.quality || null, sourceApp: 'gameflow-web'
    }, crypto.randomUUID())
    form.prompt = ''
    form.negativePrompt = ''
    if (typeof user.balance === 'number') user.balance -= 1
    notify(`任务 ${result?.taskUuid ? result.taskUuid.slice(0, 8) : ''} 已进入异步队列`)
    await loadDashboard(true)
    currentView.value = 'jobs'
  } catch (e) {
    notify(e.message, true)
  } finally {
    loading.value = false
  }
}

async function taskAction(task, action) {
  try {
    await api[action](task.taskUuid)
    notify(action === 'retry' ? '任务已重新入队' : '任务已取消')
    await loadDashboard(true)
    if (selectedTask.value?.taskUuid === task.taskUuid) selectedTask.value = tasks.value.find(item => item.taskUuid === task.taskUuid) || task
  } catch (e) {
    notify(e.message, true)
  }
}

async function showEvents(task) {
  selectedTask.value = task
  events.value = []
  detailLoading.value = true
  try {
    const result = await api.events(task.taskUuid) || []
    events.value = [...result].sort((a, b) => new Date(a.createTime) - new Date(b.createTime) || (a.id || 0) - (b.id || 0))
  } catch (e) {
    notify(e.message, true)
  } finally {
    detailLoading.value = false
  }
}

function closeDetails() { selectedTask.value = null; events.value = [] }

function logout() {
  session.clear()
  loggedIn.value = false
  tasks.value = []
  providers.value = []
  user.userId = null
  user.balance = null
  currentView.value = 'overview'
  Object.values(images).forEach(URL.revokeObjectURL)
  Object.keys(images).forEach(key => delete images[key])
}

onMounted(async () => {
  if (loggedIn.value) await loadDashboard()
  pollTimer = window.setInterval(() => { if (loggedIn.value && activeCount.value > 0) refreshActiveTasks() }, 2000)
})

onBeforeUnmount(() => {
  window.clearInterval(pollTimer)
  Object.values(images).forEach(URL.revokeObjectURL)
})
</script>

<template>
  <div v-if="!loggedIn" class="auth-shell">
    <section class="brand-panel">
      <div class="brand-mark">GF</div><p class="eyebrow">AIGC TASK INFRASTRUCTURE</p>
      <h1>让生成任务<br><span>稳定穿过流量洪峰</span></h1>
      <p class="brand-copy">Redis 限流、Outbox 可靠投递、RabbitMQ 削峰、Worker 租约与 MinIO 存储组成一条完整异步任务链路。</p>
      <div class="tech-line"><span>Redis</span><span>Outbox</span><span>RabbitMQ</span><span>Worker Lease</span><span>MinIO</span></div>
    </section>
    <section class="auth-panel">
      <form class="auth-card" @submit.prevent="authenticate">
        <p class="eyebrow">GAMEFLOW CONSOLE</p><h2>{{ authMode === 'login' ? '欢迎回来' : '创建演示账户' }}</h2>
        <p class="muted">{{ authMode === 'login' ? '登录后管理图片生成任务与执行链路' : '新用户将获得 10 点测试额度' }}</p>
        <label>用户名<input v-model="auth.username" autocomplete="username" maxlength="50" placeholder="输入用户名"></label>
        <label>密码<input v-model="auth.password" type="password" autocomplete="current-password" placeholder="至少 3 个字符"></label>
        <button class="primary wide" :disabled="loading">{{ loading ? '处理中…' : authMode === 'login' ? '登录控制台' : '立即注册' }}</button>
        <button type="button" class="text-button auth-switch" @click="authMode = authMode === 'login' ? 'register' : 'login'">{{ authMode === 'login' ? '没有账户？注册' : '已有账户？返回登录' }}</button>
      </form>
    </section>
  </div>

  <div v-else class="console-shell">
    <aside class="sidebar">
      <div class="console-logo"><span>GF</span><div><strong>GameFlow</strong><small>Generation Console</small></div></div>
      <nav>
        <button v-for="item in navItems" :key="item.id" type="button" :class="{ active: currentView === item.id }" @click="currentView = item.id">
          <span>{{ item.mark }}</span><div><strong>{{ item.label }}</strong><small>{{ item.copy }}</small></div>
        </button>
      </nav>
      <div class="sidebar-chain"><p class="eyebrow">PIPELINE</p><div><i></i><span>API 接收</span></div><div><i></i><span>Outbox 投递</span></div><div><i></i><span>Worker 执行</span></div><div><i></i><span>MinIO 存储</span></div></div>
      <div class="sidebar-account"><small>当前余额</small><strong>{{ user.balance ?? '—' }}</strong><span>生成点数</span></div>
    </aside>

    <div class="console-main">
      <header class="topbar">
        <div class="breadcrumb"><span>GameFlow</span><b>/</b><strong>{{ currentPage[1] }}</strong></div>
        <div class="header-actions"><span class="connection"><i></i>已连接</span><button class="ghost compact-button" type="button" @click="logout">退出</button></div>
      </header>

      <main class="page-shell">
        <section class="page-heading">
          <div><p class="eyebrow">{{ currentPage[0] }}</p><h1>{{ currentPage[1] }}</h1><p>{{ currentPage[2] }}</p></div>
          <button v-if="currentView !== 'create'" class="primary new-job-button" type="button" @click="currentView = 'create'">＋ 新建任务</button>
        </section>

        <template v-if="currentView === 'overview'">
          <section class="metric-grid">
            <article><span>任务总数</span><strong>{{ tasks.length }}</strong><small>当前账户</small></article>
            <article><span>正在处理</span><strong>{{ activeCount }}</strong><small>含排队与重试</small></article>
            <article><span>成功率</span><strong>{{ successRate }}%</strong><small>已结束任务</small></article>
            <article><span>平均耗时</span><strong>{{ averageLatency }}</strong><small>有耗时记录的任务</small></article>
            <article :class="{ alert: failedCount }"><span>失败任务</span><strong>{{ failedCount }}</strong><small>可在任务中心重试</small></article>
          </section>
          <section class="panel pipeline-overview">
            <div class="panel-title"><div><p class="eyebrow">RELIABLE DELIVERY</p><h2>从提交到图片入库</h2></div><span class="live-label">当前排队 {{ queuedCount }}</span></div>
            <div class="architecture-flow">
              <div><b>01</b><strong>API 接收</strong><small>JWT · 参数校验 · 幂等键</small></div><i>→</i>
              <div><b>02</b><strong>事务与 Outbox</strong><small>扣费、任务、待投递记录</small></div><i>→</i>
              <div><b>03</b><strong>RabbitMQ</strong><small>削峰 · 延迟重试 · 死信</small></div><i>→</i>
              <div><b>04</b><strong>Worker 与 Provider</strong><small>租约 · Mock / ComfyUI / 万相</small></div><i>→</i>
              <div><b>05</b><strong>MinIO 入库</strong><small>统一存储 · 前端下载</small></div>
            </div>
          </section>
          <TaskList :tasks="tasks" :images="images" :refreshing="refreshing" compact @detail="showEvents" @retry="taskAction($event, 'retry')" @cancel="taskAction($event, 'cancel')" @refresh="loadDashboard()" />
        </template>

        <template v-else-if="currentView === 'create'">
          <form class="panel generation-form" @submit.prevent="submitTask">
            <div class="form-section"><div class="form-section-title"><span>01</span><div><h2>描述你想生成的画面</h2><p>提示词会原样提交给所选 Provider。</p></div></div>
              <label>画面描述<textarea v-model="form.prompt" maxlength="2000" rows="7" placeholder="例如：未来都市夜景中的机甲少女，电影感光影，蓝紫色调"></textarea><small>{{ form.prompt.length }}/2000</small></label>
              <label>排除内容<input v-model="form.negativePrompt" maxlength="1000" placeholder="blurry, low quality（可选）"></label>
            </div>
            <div class="form-section"><div class="form-section-title"><span>02</span><div><h2>选择生成服务</h2><p>未启用的 Provider 仍会显示，但不能选择。</p></div></div><ProviderSelector v-model="form.preferredProvider" :available="providers" /></div>
            <div class="form-section compact-section"><div class="form-section-title"><span>03</span><div><h2>输出设置</h2><p>根据用途选择图片比例和质量参数。</p></div></div>
              <div class="field-row"><label>图片尺寸<select v-model="form.size"><option>1024x1024</option><option>1024x768</option><option>768x1024</option></select></label><label>质量参数<select v-model="form.quality"><option value="">Provider 默认</option><option value="standard">标准</option><option value="high">高质量</option></select></label></div>
            </div>
            <footer class="submit-bar"><div><strong>异步提交</strong><span>提交后立即返回；生成、重试与存储在后台执行。</span></div><div><span class="cost-note">预计扣除 1 点 · 余额 {{ user.balance ?? '—' }}</span><button class="primary submit-button" :disabled="loading || !providers.length">{{ loading ? '正在提交…' : '提交到任务队列 →' }}</button></div></footer>
          </form>
        </template>

        <TaskList v-else-if="currentView === 'jobs'" :tasks="tasks" :images="images" :refreshing="refreshing" @detail="showEvents" @retry="taskAction($event, 'retry')" @cancel="taskAction($event, 'cancel')" @refresh="loadDashboard()" />
        <AssetGallery v-else-if="currentView === 'assets'" :tasks="tasks" :images="images" @detail="showEvents" />
        <SystemOverview v-else-if="currentView === 'system'" :providers="providers" :last-updated="lastUpdated" />
      </main>
    </div>

    <TaskDetailDrawer v-if="selectedTask" :task="selectedTask" :events="events" :loading="detailLoading" :image="images[selectedTask.taskUuid]" @close="closeDetails" @retry="taskAction($event, 'retry')" @cancel="taskAction($event, 'cancel')" />
  </div>
  <div v-if="message || error" class="toast" :class="{ error }">{{ message || error }}</div>
</template>
