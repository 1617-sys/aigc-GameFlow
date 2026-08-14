<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api, session } from './api'

const STATUS = {
  0: { text: '排队中', className: 'pending' },
  1: { text: '生成中', className: 'running' },
  2: { text: '已完成', className: 'success' },
  3: { text: '失败', className: 'failed' },
  4: { text: '已取消', className: 'canceled' },
  5: { text: '重试中', className: 'retrying' }
}

const loggedIn = ref(Boolean(session.token()))
const authMode = ref('login')
const auth = reactive({ username: '', password: '' })
const user = reactive({ userId: null, balance: null })
const form = reactive({
  prompt: '', negativePrompt: '', preferredProvider: 'MOCK', size: '1024x1024'
})
const tasks = ref([])
const providers = ref(['MOCK'])
const images = reactive({})
const events = ref([])
const selectedTask = ref(null)
const loading = ref(false)
const refreshing = ref(false)
const message = ref('')
const error = ref('')
let pollTimer

const activeCount = computed(() => tasks.value.filter(t => [0, 1, 5].includes(t.status)).length)
const successCount = computed(() => tasks.value.filter(t => t.status === 2).length)

function notify(text, isError = false) {
  message.value = isError ? '' : text
  error.value = isError ? text : ''
  window.setTimeout(() => {
    if (message.value === text) message.value = ''
    if (error.value === text) error.value = ''
  }, 3200)
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
    providers.value = providerData?.length ? providerData : ['MOCK']
    if (!providers.value.includes(form.preferredProvider)) form.preferredProvider = providers.value[0]
    await loadFinishedImages()
  } catch (e) {
    if (!session.token()) logout()
    if (!silent) notify(e.message, true)
  } finally {
    refreshing.value = false
  }
}

async function loadFinishedImages() {
  for (const task of tasks.value) {
    if (task.status === 2 && !images[task.taskUuid]) {
      try { images[task.taskUuid] = await api.image(task.taskUuid) } catch { /* retry next poll */ }
    }
  }
}

async function refreshActiveTasks() {
  const activeTasks = tasks.value.filter(task => [0, 1, 5].includes(task.status))
  if (!activeTasks.length) return
  try {
    const updates = await api.taskStatuses(activeTasks.map(task => task.taskUuid))
    const updateMap = new Map(updates.map(task => [task.taskUuid, task]))
    tasks.value = tasks.value.map(task => updateMap.get(task.taskUuid) || task)
    await loadFinishedImages()
  } catch (e) {
    if (!session.token()) logout()
  }
}

async function submitTask() {
  if (!form.prompt.trim()) return notify('请先填写画面描述', true)
  loading.value = true
  try {
    const key = crypto.randomUUID()
    await api.submit({
      prompt: form.prompt.trim(),
      negativePrompt: form.negativePrompt.trim() || null,
      preferredProvider: form.preferredProvider,
      size: form.size,
      sourceApp: 'gameflow-web'
    }, key)
    form.prompt = ''
    if (typeof user.balance === 'number') user.balance -= 1
    notify('任务已进入队列')
    await loadDashboard(true)
  } catch (e) {
    notify(e.message, true)
  } finally {
    loading.value = false
  }
}

async function taskAction(task, action) {
  try {
    await api[action](task.taskUuid)
    notify(action === 'retry' ? '已重新入队' : '任务已取消')
    await loadDashboard(true)
  } catch (e) {
    notify(e.message, true)
  }
}

async function showEvents(task) {
  selectedTask.value = task
  events.value = []
  try { events.value = await api.events(task.taskUuid) || [] } catch (e) { notify(e.message, true) }
}

function closeEvents() {
  selectedTask.value = null
  events.value = []
}

function logout() {
  session.clear()
  loggedIn.value = false
  tasks.value = []
  user.userId = null
  user.balance = null
  Object.values(images).forEach(URL.revokeObjectURL)
  Object.keys(images).forEach(key => delete images[key])
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function shortId(value) {
  return value ? `${value.slice(0, 8)}…${value.slice(-4)}` : '—'
}

onMounted(async () => {
  if (loggedIn.value) await loadDashboard()
  pollTimer = window.setInterval(() => {
    if (loggedIn.value && activeCount.value > 0) refreshActiveTasks()
  }, 2000)
})

onBeforeUnmount(() => {
  window.clearInterval(pollTimer)
  Object.values(images).forEach(URL.revokeObjectURL)
})
</script>

<template>
  <div v-if="!loggedIn" class="auth-shell">
    <section class="brand-panel">
      <div class="brand-mark">GF</div>
      <p class="eyebrow">AIGC TASK INFRASTRUCTURE</p>
      <h1>让生成任务<br><span>稳定穿过流量洪峰</span></h1>
      <p class="brand-copy">Redis 限流、RabbitMQ 削峰、状态机与对象存储组成的一条完整异步任务链路。</p>
      <div class="tech-line"><span>Redis</span><span>RabbitMQ</span><span>MySQL</span><span>MinIO</span></div>
    </section>
    <section class="auth-panel">
      <form class="auth-card" @submit.prevent="authenticate">
        <p class="eyebrow">GAMEFLOW CONSOLE</p>
        <h2>{{ authMode === 'login' ? '欢迎回来' : '创建演示账户' }}</h2>
        <p class="muted">{{ authMode === 'login' ? '登录后管理你的图片生成任务' : '新用户将获得 10 点测试额度' }}</p>
        <label>用户名<input v-model="auth.username" autocomplete="username" maxlength="50" placeholder="输入用户名"></label>
        <label>密码<input v-model="auth.password" type="password" autocomplete="current-password" placeholder="至少 3 个字符"></label>
        <button class="primary wide" :disabled="loading">{{ loading ? '处理中…' : authMode === 'login' ? '登录控制台' : '立即注册' }}</button>
        <button type="button" class="text-button" @click="authMode = authMode === 'login' ? 'register' : 'login'">
          {{ authMode === 'login' ? '没有账户？注册' : '已有账户？返回登录' }}
        </button>
      </form>
    </section>
  </div>

  <div v-else class="app-shell">
    <header class="topbar">
      <div class="logo"><span>GF</span><div><strong>GameFlow</strong><small>Generation Console</small></div></div>
      <div class="header-actions">
        <span class="health"><i></i>控制面在线</span>
        <span v-if="user.balance !== null" class="balance">余额 {{ user.balance }}</span>
        <button class="ghost compact" @click="logout">退出</button>
      </div>
    </header>

    <main class="dashboard">
      <section class="hero-row">
        <div><p class="eyebrow">ASYNC IMAGE WORKSPACE</p><h1>生成任务控制台</h1><p class="muted">提交快速返回，耗时工作在队列中受控执行。</p></div>
        <div class="stats">
          <div><strong>{{ tasks.length }}</strong><span>近期任务</span></div>
          <div><strong>{{ activeCount }}</strong><span>正在处理</span></div>
          <div><strong>{{ successCount }}</strong><span>生成成功</span></div>
        </div>
      </section>

      <section class="workspace-grid">
        <form class="panel create-panel" @submit.prevent="submitTask">
          <div class="panel-title"><div><p class="eyebrow">NEW JOB</p><h2>创建生成任务</h2></div><span class="async-tag">异步</span></div>
          <label>画面描述<textarea v-model="form.prompt" maxlength="2000" rows="6" placeholder="例如：未来都市夜景中的机甲少女，电影感光影，蓝紫色调"></textarea><small>{{ form.prompt.length }}/2000</small></label>
          <label>排除内容<input v-model="form.negativePrompt" maxlength="1000" placeholder="blurry, low quality（可选）"></label>
          <div class="field-row">
            <label>生成服务<select v-model="form.preferredProvider"><option v-for="item in providers" :key="item">{{ item }}</option></select></label>
            <label>图片尺寸<select v-model="form.size"><option>1024x1024</option><option>1024x768</option><option>768x1024</option></select></label>
          </div>
          <button class="primary wide" :disabled="loading"><span>{{ loading ? '正在提交…' : '提交到任务队列' }}</span><b>→</b></button>
          <p class="form-hint">每次提交生成独立幂等键；Docker 默认使用免费 Mock Provider。</p>
        </form>

        <section class="panel task-panel">
          <div class="panel-title"><div><p class="eyebrow">RECENT JOBS</p><h2>最近任务</h2></div><button class="ghost compact" :class="{ spinning: refreshing }" @click="loadDashboard()">↻ 刷新</button></div>
          <div v-if="!tasks.length" class="empty"><div>◇</div><h3>还没有任务</h3><p>提交第一条画面描述，观察完整异步链路。</p></div>
          <div v-else class="task-list">
            <article v-for="task in tasks" :key="task.taskUuid" class="task-card">
              <div class="thumb" :class="STATUS[task.status]?.className">
                <img v-if="images[task.taskUuid]" :src="images[task.taskUuid]" alt="生成结果">
                <span v-else-if="[0, 1, 5].includes(task.status)" class="loader"></span>
                <span v-else>{{ task.status === 3 ? '!' : '×' }}</span>
              </div>
              <div class="task-info">
                <div class="task-head"><span class="status" :class="STATUS[task.status]?.className">{{ STATUS[task.status]?.text || '未知' }}</span><time>{{ formatTime(task.createTime) }}</time></div>
                <h3>{{ task.prompt }}</h3>
                <p><span>{{ task.provider || 'AUTO' }}</span><span>{{ task.size || '默认尺寸' }}</span><span># {{ shortId(task.taskUuid) }}</span></p>
                <p v-if="task.errorMsg" class="task-error">{{ task.errorMsg }}</p>
                <div class="task-actions">
                  <button class="text-button" @click="showEvents(task)">执行事件</button>
                  <button v-if="[0, 1, 5].includes(task.status)" class="text-button danger" @click="taskAction(task, 'cancel')">取消</button>
                  <button v-if="task.status === 3" class="text-button" @click="taskAction(task, 'retry')">重新生成</button>
                </div>
              </div>
            </article>
          </div>
        </section>
      </section>
    </main>

    <div v-if="selectedTask" class="modal-backdrop" @click.self="closeEvents">
      <section class="modal">
        <div class="panel-title"><div><p class="eyebrow">EVENT STREAM</p><h2>任务执行轨迹</h2></div><button class="close" @click="closeEvents">×</button></div>
        <p class="modal-id">{{ selectedTask.taskUuid }}</p>
        <div class="timeline">
          <div v-for="event in events" :key="event.id" class="event"><i></i><div><strong>{{ event.eventType }}</strong><p>{{ event.message }}</p><time>{{ formatTime(event.createTime) }}</time></div></div>
          <p v-if="!events.length" class="muted">暂无事件记录</p>
        </div>
      </section>
    </div>
  </div>

  <div v-if="message || error" class="toast" :class="{ error }">{{ message || error }}</div>
</template>
