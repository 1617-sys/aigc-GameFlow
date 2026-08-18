<script setup>
const props = defineProps({
  available: { type: Array, default: () => [] },
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const catalog = [
  { id: 'WANX', name: '阿里云万相', kind: '云端', copy: '高质量通用生图，适合正式任务。' },
  { id: 'COMFYUI', name: 'ComfyUI', kind: '本地', copy: '支持工作流、LoRA 与自有模型。' },
  { id: 'MOCK', name: 'Mock', kind: '测试', copy: '验证异步链路，不产生真实图片。' }
]

function choose(provider) {
  if (props.available.includes(provider.id)) emit('update:modelValue', provider.id)
}
</script>

<template>
  <div class="provider-grid">
    <button
      v-for="provider in catalog"
      :key="provider.id"
      type="button"
      class="provider-card"
      :class="{ selected: modelValue === provider.id, unavailable: !available.includes(provider.id) }"
      :disabled="!available.includes(provider.id)"
      @click="choose(provider)"
    >
      <span class="provider-radio"><i></i></span>
      <span class="provider-copy">
        <strong>{{ provider.name }}</strong>
        <small>{{ provider.copy }}</small>
      </span>
      <span class="provider-state">
        <b>{{ provider.kind }}</b>
        <small>{{ available.includes(provider.id) ? '可用' : '未启用' }}</small>
      </span>
    </button>
  </div>
</template>
