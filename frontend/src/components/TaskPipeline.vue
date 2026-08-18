<script setup>
import { computed } from 'vue'

const props = defineProps({
  task: { type: Object, required: true },
  events: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false }
})

const stageDefinitions = [
  { id: 'created', label: '已接收', events: ['TASK_CREATED'] },
  { id: 'queued', label: '已入队', events: ['TASK_QUEUED'] },
  { id: 'running', label: 'Worker 执行', events: ['TASK_RUNNING', 'PROVIDER_SELECTED', 'PROVIDER_REQUEST_SENT'] },
  { id: 'stored', label: '已存储', events: ['IMAGE_STORED'] },
  { id: 'success', label: '已完成', events: ['TASK_SUCCESS'] }
]

const seenEvents = computed(() => new Set(props.events.map(event => event.eventType)))

const eventProgress = computed(() => stageDefinitions.reduce((lastIndex, stage, index) =>
  stage.events.some(type => seenEvents.value.has(type)) ? index : lastIndex, -1))

const fallbackProgress = computed(() => {
  if (props.task.status === 2) return 5
  if (props.task.status === 1) return 3
  if (props.task.status === 5) return 2
  if (props.task.status === 0) return 1
  return 1
})

const stages = computed(() => stageDefinitions.map((stage, index) => ({
  ...stage,
  done: stage.events.some(type => seenEvents.value.has(type)) || index < fallbackProgress.value,
  failed: props.task.status === 3 && index === Math.max(0, eventProgress.value >= 0 ? eventProgress.value : fallbackProgress.value - 1)
})))
</script>

<template>
  <div class="task-pipeline" :class="{ compact }">
    <div v-for="(stage, index) in stages" :key="stage.id" class="pipeline-stage" :class="{ done: stage.done, failed: stage.failed }">
      <span class="stage-dot">{{ stage.failed ? '!' : stage.done ? '✓' : index + 1 }}</span>
      <strong>{{ stage.label }}</strong>
    </div>
  </div>
</template>
