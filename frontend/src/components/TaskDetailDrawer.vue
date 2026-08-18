<script setup>
import TaskPipeline from './TaskPipeline.vue'

defineProps({
  task: { type: Object, required: true },
  events: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  image: { type: String, default: '' }
})

defineEmits(['close', 'retry', 'cancel'])

const eventMeta = {
  TASK_CREATED: ['任务已创建', '请求已通过校验并写入数据库'],
  TASK_QUEUED: ['已进入消息队列', 'Outbox 已将任务可靠投递至 RabbitMQ'],
  TASK_RUNNING: ['Worker 开始执行', 'Worker 已取得任务租约'],
  TASK_CANCELED: ['任务已取消', '任务不会继续执行'],
  TASK_RETRY_REQUESTED: ['用户请求重试', '失败任务正在重新入队'],
  TASK_RETRY_SCHEDULED: ['已安排延迟重试', '任务将在重试队列等待后再次执行'],
  TASK_LEASE_EXPIRED: ['执行租约已过期', '恢复任务正在接管并重新调度'],
  TASK_RESULT_IGNORED: ['忽略迟到结果', '旧 Worker 的结果未覆盖最新状态'],
  TASK_DEAD_LETTERED: ['已进入死信队列', '任务达到最大重试次数'],
  PROVIDER_SELECTED: ['已选择生成服务', '路由器已确定实际执行的 Provider'],
  PROVIDER_REQUEST_SENT: ['已发送生成请求', '正在等待图片服务返回结果'],
  IMAGE_STORED: ['图片已保存', '生成结果已转存到 MinIO'],
  CALLBACK_SENT: ['回调已发送', '外部系统已收到任务结果'],
  CALLBACK_FAILED: ['回调发送失败', '生成结果不受影响，可检查回调地址'],
  TASK_SUCCESS: ['任务执行成功', '图片可以预览和下载'],
  TASK_FAILED: ['任务执行失败', '可查看错误信息后重新生成']
}

function labelFor(event) {
  return eventMeta[event.eventType]?.[0] || event.eventType
}

function copyFor(event) {
  return eventMeta[event.eventType]?.[1] || event.message
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}
</script>

<template>
  <div class="drawer-backdrop" @click.self="$emit('close')">
    <aside class="task-drawer">
      <header class="drawer-header">
        <div><p class="eyebrow">TASK DETAILS</p><h2>任务详情与执行链路</h2></div>
        <button class="close" type="button" aria-label="关闭" @click="$emit('close')">×</button>
      </header>

      <div class="drawer-scroll">
        <section class="detail-hero">
          <div class="detail-preview">
            <img v-if="image" :src="image" :alt="task.prompt">
            <span v-else>{{ task.status === 3 ? '生成失败' : '等待图片' }}</span>
          </div>
          <div>
            <span class="detail-status">{{ task.status === 2 ? '已完成' : task.status === 3 ? '失败' : task.status === 1 ? '生成中' : task.status === 5 ? '重试中' : task.status === 4 ? '已取消' : '排队中' }}</span>
            <h3>{{ task.prompt }}</h3>
            <p v-if="task.errorMsg" class="detail-error">{{ task.errorMsg }}</p>
          </div>
        </section>

        <section class="drawer-section">
          <div class="section-heading"><div><p class="eyebrow">PIPELINE</p><h3>当前执行进度</h3></div><span>异步任务链路</span></div>
          <TaskPipeline :task="task" :events="events" />
        </section>

        <section class="drawer-section">
          <div class="section-heading"><div><p class="eyebrow">METADATA</p><h3>任务信息</h3></div></div>
          <dl class="metadata-grid">
            <div><dt>Provider</dt><dd>{{ task.provider || 'AUTO' }}</dd></div>
            <div><dt>模型</dt><dd>{{ task.model || '默认模型' }}</dd></div>
            <div><dt>图片尺寸</dt><dd>{{ task.size || '默认尺寸' }}</dd></div>
            <div><dt>生成耗时</dt><dd>{{ task.latencyMs != null ? `${(task.latencyMs / 1000).toFixed(1)} 秒` : '—' }}</dd></div>
            <div><dt>重试次数</dt><dd>{{ task.retryCount ?? 0 }}</dd></div>
            <div><dt>Worker</dt><dd>{{ task.workerId || '—' }}</dd></div>
            <div><dt>Provider Job ID</dt><dd>{{ task.providerJobId || '—' }}</dd></div>
            <div><dt>Trace ID</dt><dd>{{ task.traceId || '—' }}</dd></div>
          </dl>
        </section>

        <section class="drawer-section">
          <div class="section-heading"><div><p class="eyebrow">EVENT STREAM</p><h3>执行事件</h3></div><span>{{ events.length }} 条记录</span></div>
          <div v-if="loading" class="drawer-loading">正在读取执行事件…</div>
          <div v-else class="event-timeline">
            <article v-for="event in events" :key="event.id" class="event-row">
              <i></i>
              <div>
                <div class="event-title"><strong>{{ labelFor(event) }}</strong><code>{{ event.eventType }}</code></div>
                <p>{{ copyFor(event) }}</p>
                <small v-if="event.message && event.message !== copyFor(event)">{{ event.message }}</small>
                <time>{{ formatTime(event.createTime) }}</time>
              </div>
            </article>
            <p v-if="!events.length" class="muted">暂无事件记录</p>
          </div>
        </section>
      </div>

      <footer class="drawer-actions">
        <button v-if="[0, 1, 5].includes(task.status)" class="ghost danger" type="button" @click="$emit('cancel', task)">取消任务</button>
        <button v-if="task.status === 3" class="primary" type="button" @click="$emit('retry', task)">重新生成</button>
        <button class="ghost" type="button" @click="$emit('close')">关闭</button>
      </footer>
    </aside>
  </div>
</template>
