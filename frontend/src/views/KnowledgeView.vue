<template>
  <div class="knowledge-page page-shell">
    <section class="toolbar soft-card">
      <div>
        <h3 class="section-title">关系检索</h3>
        <p class="section-copy">可按品种、病害、虫害或药剂名称搜索。没有 Neo4j 时系统会自动使用本地演示图谱。</p>
      </div>

      <div class="toolbar-actions">
        <el-input
          v-model="searchKeyword"
          clearable
          placeholder="输入关键词，例如：桂味、炭疽病、咪鲜胺"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>
        <el-button :icon="Refresh" :loading="loading" @click="refreshGraph">刷新</el-button>
      </div>

      <div class="pill-row">
        <button v-for="keyword in hotKeywords" :key="keyword" class="chip-button" type="button" @click="applyKeyword(keyword)">
          {{ keyword }}
        </button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">节点数量</div>
        <div class="metric-value">{{ graphData.nodes.length }}</div>
        <div class="metric-note">用于展示图谱规模和搜索结果范围。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">关系数量</div>
        <div class="metric-value">{{ graphData.edges.length }}</div>
        <div class="metric-note">图谱会保留与当前查询节点相关的主要关系链路。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">当前焦点</div>
        <div class="metric-value focus-value">{{ selectedNode ? displayName(selectedNode) : '未选择' }}</div>
        <div class="metric-note">点击任意节点查看右侧详细属性。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">图谱模式</div>
        <div class="metric-value focus-value">{{ graphData.nodes.length ? '已加载' : '待查询' }}</div>
        <div class="metric-note">离线模式下仍可演示核心节点和边的关系。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="graph-panel glass-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">可视化关系图</h3>
            <p class="section-copy">颜色区分节点类型，线条标签表示关系类型。</p>
          </div>
          <div class="legend">
            <span v-for="item in legends" :key="item.label">
              <i :style="{ backgroundColor: item.color }" />
              {{ item.text }}
            </span>
          </div>
        </header>

        <div v-if="!positionedNodes.length" class="empty-wrapper">
          <el-empty description="当前没有可展示的图谱结果，试试更换关键词。" />
        </div>

        <svg v-else :viewBox="`0 0 ${canvasWidth} ${canvasHeight}`" class="graph-svg">
          <g v-for="edge in edgeLines" :key="`${edge.source}-${edge.target}-${edge.label}`">
            <line :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" class="edge-line" />
            <text :x="edge.labelX" :y="edge.labelY" class="edge-label">{{ edge.label }}</text>
          </g>

          <g
            v-for="node in positionedNodes"
            :key="node.id"
            class="node"
            @click="selectedNode = node"
          >
            <circle
              :cx="node.x"
              :cy="node.y"
              :r="selectedNode?.id === node.id ? 32 : 27"
              :fill="node.color"
              :stroke="selectedNode?.id === node.id ? '#ffd26f' : 'rgba(255,255,255,0.84)'"
              :stroke-width="selectedNode?.id === node.id ? 4 : 2"
            />
            <text :x="node.x" :y="node.y + 5" class="node-text">
              {{ displayName(node) }}
            </text>
          </g>
        </svg>
      </article>

      <article class="detail-panel soft-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">节点详情</h3>
            <p class="section-copy">适合答辩时停留讲解某个品种或病害的属性信息。</p>
          </div>
        </header>

        <el-empty v-if="!selectedNode" description="点击左侧任意节点查看详情。" />

        <div v-else class="detail-content">
          <div class="detail-heading">
            <strong>{{ displayName(selectedNode) }}</strong>
            <el-tag effect="plain">{{ selectedNode.label }}</el-tag>
          </div>

          <div class="property-list">
            <div v-for="[key, value] in entries(selectedNode.properties)" :key="key" class="property-item">
              <span class="property-key">{{ key }}</span>
              <span class="property-value">{{ formatValue(value) }}</span>
            </div>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import { knowledgeGraphAPI, type KnowledgeGraphNode, type KnowledgeGraphResponse } from '@/api'

type PositionedNode = KnowledgeGraphNode & { x: number; y: number; color: string }
type PositionedEdge = {
  source: string
  target: string
  label: string
  x1: number
  y1: number
  x2: number
  y2: number
  labelX: number
  labelY: number
}

const canvasWidth = 1040
const canvasHeight = 640

const loading = ref(false)
const searchKeyword = ref('')
const graphData = ref<KnowledgeGraphResponse>({ nodes: [], edges: [] })
const selectedNode = ref<PositionedNode | null>(null)

const hotKeywords = ['桂味', '炭疽病', '霜疫霉病', '咪鲜胺', '蒂蛀虫']

const legends = [
  { label: 'LitchiVariety', text: '品种', color: '#2f6a59' },
  { label: 'Disease', text: '病害', color: '#c65d1a' },
  { label: 'Pest', text: '虫害', color: '#d97706' },
  { label: 'Pesticide', text: '药剂', color: '#2f855a' },
  { label: 'CultivationTechnique', text: '技术', color: '#6b7280' }
]

const labelColor = (label: string) => {
  const palette: Record<string, string> = {
    LitchiVariety: '#2f6a59',
    Disease: '#c65d1a',
    Pest: '#d97706',
    Pesticide: '#2f855a',
    CultivationTechnique: '#54656f'
  }

  return palette[label] ?? '#64748b'
}

const positionedNodes = computed<PositionedNode[]>(() => {
  const nodes = graphData.value.nodes
  if (!nodes.length) {
    return []
  }

  const radius = Math.max(170, Math.min(canvasWidth, canvasHeight) / 2 - 96)
  const centerX = canvasWidth / 2
  const centerY = canvasHeight / 2

  return nodes.map((node, index) => {
    if (nodes.length === 1) {
      return {
        ...node,
        x: centerX,
        y: centerY,
        color: labelColor(node.label)
      }
    }

    const angle = (Math.PI * 2 * index) / nodes.length - Math.PI / 2
    return {
      ...node,
      x: centerX + Math.cos(angle) * radius,
      y: centerY + Math.sin(angle) * radius,
      color: labelColor(node.label)
    }
  })
})

const edgeLines = computed<PositionedEdge[]>(() => {
  const nodeMap = new Map(positionedNodes.value.map(node => [node.id, node]))
  return graphData.value.edges
    .map(edge => {
      const source = nodeMap.get(edge.source)
      const target = nodeMap.get(edge.target)

      if (!source || !target) {
        return null
      }

      return {
        ...edge,
        x1: source.x,
        y1: source.y,
        x2: target.x,
        y2: target.y,
        labelX: (source.x + target.x) / 2,
        labelY: (source.y + target.y) / 2
      }
    })
    .filter((edge): edge is PositionedEdge => edge !== null)
})

const displayName = (node: KnowledgeGraphNode) => {
  const name = node.properties.name
  return typeof name === 'string' && name.trim() ? name : node.label
}

const entries = (properties: Record<string, unknown>) => Object.entries(properties)

const formatValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return value.join('、')
  }

  if (value && typeof value === 'object') {
    return JSON.stringify(value, null, 0)
  }

  return String(value ?? '')
}

const loadGraphData = async (keyword?: string) => {
  loading.value = true
  try {
    const response = await knowledgeGraphAPI.visualize(keyword)
    graphData.value = response.data
    selectedNode.value = positionedNodes.value[0] ?? null
  } catch (error) {
    ElMessage.error('加载知识图谱失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  loadGraphData(searchKeyword.value)
}

const refreshGraph = () => {
  loadGraphData(searchKeyword.value)
}

const applyKeyword = (keyword: string) => {
  searchKeyword.value = keyword
  handleSearch()
}

onMounted(() => {
  loadGraphData()
})
</script>

<style scoped>
.knowledge-page {
  gap: 18px;
}

.toolbar,
.detail-panel {
  padding: 22px;
}

.toolbar {
  display: grid;
  gap: 16px;
}

.toolbar-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
}

.graph-panel {
  padding: 22px;
}

.panel-header {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 18px;
}

.legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--ink-soft);
  font-size: 13px;
}

.legend span {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.empty-wrapper {
  min-height: 640px;
  display: grid;
  place-items: center;
}

.graph-svg {
  width: 100%;
  min-height: 640px;
  border-radius: 24px;
  background:
    radial-gradient(circle at center, rgba(47, 106, 89, 0.1), transparent 56%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(245, 247, 241, 0.96));
}

.edge-line {
  stroke: rgba(92, 111, 103, 0.65);
  stroke-width: 2;
}

.edge-label {
  fill: #5d6e67;
  font-size: 12px;
  text-anchor: middle;
}

.node {
  cursor: pointer;
}

.node-text {
  fill: #fffdf7;
  font-size: 12px;
  font-weight: 700;
  text-anchor: middle;
  pointer-events: none;
}

.detail-content {
  display: grid;
  gap: 18px;
}

.detail-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.detail-heading strong {
  color: var(--ink-strong);
  font-size: 24px;
}

.property-list {
  display: grid;
  gap: 12px;
}

.property-item {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.property-key {
  color: var(--ink-soft);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.property-value {
  color: var(--ink-strong);
  line-height: 1.7;
}

.focus-value {
  font-size: 18px;
  line-height: 1.4;
}

@media (max-width: 1180px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .toolbar-actions {
    grid-template-columns: 1fr;
  }

  .graph-svg,
  .empty-wrapper {
    min-height: 520px;
  }
}
</style>
