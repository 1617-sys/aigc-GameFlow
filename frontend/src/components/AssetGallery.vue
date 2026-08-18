<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  tasks: { type: Array, default: () => [] },
  images: { type: Object, default: () => ({}) }
})

defineEmits(['detail'])

const query = ref('')
const providerFilter = ref('all')
const sortOrder = ref('newest')

const successfulTasks = computed(() => props.tasks.filter(task => task.status === 2))
const providers = computed(() => [...new Set(successfulTasks.value.map(task => task.provider).filter(Boolean))])
const visibleAssets = computed(() => successfulTasks.value
  .filter(task => (!query.value || task.prompt?.toLowerCase().includes(query.value.toLowerCase())) && (providerFilter.value === 'all' || task.provider === providerFilter.value))
  .sort((a, b) => sortOrder.value === 'newest'
    ? new Date(b.updateTime || b.createTime) - new Date(a.updateTime || a.createTime)
    : new Date(a.updateTime || a.createTime) - new Date(b.updateTime || b.createTime)))

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}
</script>

<template>
  <section class="panel asset-library">
    <div class="panel-title">
      <div><p class="eyebrow">GENERATED ASSETS</p><h2>生成素材库</h2></div>
      <span class="storage-note">MySQL 元数据 · MinIO 原图</span>
    </div>
    <p class="section-copy">成功任务会自动进入素材库。可以按提示词和 Provider 查找，并回到来源任务查看完整执行链路。</p>
    <div class="asset-toolbar">
      <input v-model="query" type="search" placeholder="搜索提示词">
      <select v-model="providerFilter"><option value="all">全部 Provider</option><option v-for="provider in providers" :key="provider">{{ provider }}</option></select>
      <select v-model="sortOrder"><option value="newest">最新生成</option><option value="oldest">最早生成</option></select>
    </div>

    <div v-if="!visibleAssets.length" class="empty-state"><span>▧</span><h3>素材库暂时为空</h3><p>成功完成的图片会自动出现在这里。</p></div>
    <div v-else class="asset-grid">
      <article v-for="task in visibleAssets" :key="task.taskUuid" class="asset-card">
        <button class="asset-preview" type="button" @click="$emit('detail', task)">
          <img v-if="images[task.taskUuid]" :src="images[task.taskUuid]" :alt="task.prompt" loading="lazy">
          <span v-else class="loader"></span>
          <b v-if="task.provider === 'MOCK'" class="mock-watermark">MOCK PLACEHOLDER</b>
        </button>
        <div class="asset-meta">
          <h3>{{ task.prompt }}</h3>
          <p><span>{{ task.provider }}</span><span>{{ task.model || '默认模型' }}</span><span>{{ task.size || '默认尺寸' }}</span></p>
          <time>{{ formatTime(task.updateTime) }}</time>
          <div class="asset-actions">
            <button class="text-button" type="button" @click="$emit('detail', task)">来源任务</button>
            <a v-if="images[task.taskUuid]" class="text-button" :href="images[task.taskUuid]" :download="`${task.taskUuid}.png`">下载原图</a>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
