<script setup>
defineProps({
  providers: { type: Array, default: () => [] },
  lastUpdated: { type: String, default: '' }
})

const providerCatalog = [
  ['WANX', '阿里云万相', '云端图片生成'],
  ['COMFYUI', 'ComfyUI', '本地工作流与 LoRA'],
  ['MOCK', 'Mock', '链路测试 Provider']
]
</script>

<template>
  <div class="system-layout">
    <section class="panel system-card system-intro">
      <p class="eyebrow">CONTROL PLANE</p><h2>系统状态</h2>
      <p>这里区分“已经确认的连接状态”和“后端暂未提供的监控数据”，不会用静态绿点伪装完整健康检查。</p>
      <div class="connection-state"><i></i><div><strong>控制台已连接</strong><small>任务和 Provider 接口可以正常响应</small></div></div>
      <small class="last-updated">最近刷新：{{ lastUpdated || '—' }}</small>
    </section>

    <section class="panel system-card">
      <div class="panel-title"><div><p class="eyebrow">PROVIDERS</p><h2>生成服务</h2></div></div>
      <div class="health-list">
        <div v-for="provider in providerCatalog" :key="provider[0]" class="health-row">
          <span class="health-icon" :class="{ online: providers.includes(provider[0]) }"></span>
          <div><strong>{{ provider[1] }}</strong><small>{{ provider[2] }}</small></div>
          <b :class="{ online: providers.includes(provider[0]) }">{{ providers.includes(provider[0]) ? '已启用' : '未启用' }}</b>
        </div>
      </div>
    </section>

    <section class="panel system-card infrastructure-card">
      <div class="panel-title"><div><p class="eyebrow">INFRASTRUCTURE</p><h2>链路组件</h2></div><span class="pending-api">等待健康接口</span></div>
      <div class="infra-grid">
        <div><strong>MySQL</strong><span>任务、Outbox 与事件</span><small>状态未探测</small></div>
        <div><strong>Redis</strong><span>限流、幂等与缓存</span><small>状态未探测</small></div>
        <div><strong>RabbitMQ</strong><span>任务、重试与死信队列</span><small>状态未探测</small></div>
        <div><strong>MinIO</strong><span>生成图片对象存储</span><small>状态未探测</small></div>
      </div>
      <p class="system-note">后续增加后端健康汇总接口后，这里可以展示队列积压、Worker 数量、存储状态和 Provider 延迟。</p>
    </section>
  </div>
</template>
