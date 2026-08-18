<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  tasks: { type: Array, default: () => [] },
  images: { type: Object, default: () => ({}) },
  refreshing: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

defineEmits(['detail', 'retry', 'cancel', 'refresh'])

const query = ref('')
const statusFilter = ref('all')
const providerFilter = ref('all')

const statusMeta = {
  0: ['排队中', 'pending'],
  1: ['生成中', 'running'],
  2: ['已完成', 'success'],
  3: ['失败', 'failed'],
  4: ['已取消', 'canceled'],
  5: ['重试中', 'retrying']
}

const providers = computed(() => [...new Set(props.tasks.map(task => task.provider).filter(Boolean))])

const filteredTasks = computed(() => props.tasks.filter(task => {
  const matchesQuery = !query.value || task.prompt?.toLowerCase().includes(query.value.toLowerCase()) || task.taskUuid?.includes(query.value)
  const matchesStatus = statusFilter.value === 'all' || String(task.status) === statusFilter.value
  const matchesProvider = providerFilter.value === 'all' || task.provider === providerFilter.value
  return matchesQuery && matchesStatus && matchesProvider
}))

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function shortId(value) {
  return value ? `${value.slice(0, 8)}…${value.slice(-4)}` : '—'
}
</script>

<template>
  <section class="panel task-center" :class="{ compact }">
    <div class="panel-title">
      <div><p class="eyebrow">JOB CENTER</p><h2>{{ compact ? '最新任务' : '任务中心' }}</h2></div>
      <button class="ghost compact-button" :class="{ spinning: refreshing }" type="button" @click="$emit('refresh')">↻ 刷新</button>
    </div>

    <div v-if="!compact" class="task-toolbar">
      <input v-model="query" type="search" placeholder="搜索提示词或任务编号">
      <select v-model="statusFilter">
        <option value="all">全部状态</option><option value="0">排队中</option><option value="1">生成中</option>
        <option value="5">重试中</option><option value="2">已完成</option><option value="3">失败</option><option value="4">已取消</option>
      </select>
      <select v-model="providerFilter"><option value="all">全部 Provider</option><option v-for="provider in providers" :key="provider">{{ provider }}</option></select>
    </div>

    <div v-if="!filteredTasks.length" class="empty-state">
      <span>◇</span><h3>{{ tasks.length ? '没有符合条件的任务' : '还没有任务' }}</h3><p>提交画面描述后，可以在这里观察完整异步链路。</p>
    </div>

    <div v-else class="job-table">
      <article v-for="task in (compact ? filteredTasks.slice(0, 4) : filteredTasks)" :key="task.taskUuid" class="job-row" @click="$emit('detail', task)">
        <div class="job-thumb" :class="statusMeta[task.status]?.[1]">
          <img v-if="images[task.taskUuid]" :src="images[task.taskUuid]" loading="lazy" alt="生成结果">
          <span v-else-if="[0, 1, 5].includes(task.status)" class="loader"></span>
          <span v-else>{{ task.status === 3 ? '!' : task.provider === 'MOCK' ? 'M' : '×' }}</span>
        </div>
        <div class="job-main">
          <div class="job-title-line">
            <span class="status" :class="statusMeta[task.status]?.[1]">{{ statusMeta[task.status]?.[0] || '未知' }}</span>
            <h3>{{ task.prompt }}</h3>
          </div>
          <p><span>{{ task.provider || 'AUTO' }}</span><span>{{ task.model || '默认模型' }}</span><span>{{ task.size || '默认尺寸' }}</span></p>
          <p v-if="task.errorMsg" class="job-error">{{ task.errorMsg }}</p>
        </div>
        <div class="job-metrics">
          <time>{{ formatTime(task.createTime) }}</time>
          <small>{{ task.latencyMs != null ? `耗时 ${(task.latencyMs / 1000).toFixed(1)}s` : `# ${shortId(task.taskUuid)}` }}</small>
          <small v-if="task.retryCount">重试 {{ task.retryCount }} 次</small>
        </div>
        <div class="job-actions" @click.stop>
          <button class="text-button" type="button" @click="$emit('detail', task)">查看链路</button>
          <button v-if="[0, 1, 5].includes(task.status)" class="text-button danger" type="button" @click="$emit('cancel', task)">取消</button>
          <button v-if="task.status === 3" class="text-button" type="button" @click="$emit('retry', task)">重试</button>
        </div>
      </article>
    </div>
  </section>
</template>
