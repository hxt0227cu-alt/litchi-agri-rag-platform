<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h2>知识图谱</h2>
        <p>支持关键词查询、节点关系展示和基础属性查看。</p>
      </div>
    </header>

    <div class="toolbar">
      <el-input
        v-model="searchKeyword"
        clearable
        placeholder="输入品种、病害、害虫或农药关键词"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
      <el-button :icon="Refresh" :loading="loading" @click="refreshGraph">刷新</el-button>
    </div>

    <div class="content">
      <el-card class="graph-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <span>关系图</span>
            <el-tag type="info" effect="plain">
              {{ graphData.nodes.length }} 个节点 / {{ graphData.edges.length }} 条关系
            </el-tag>
          </div>
        </template>

        <div v-if="!positionedNodes.length" class="empty-wrapper">
          <el-empty description="暂无图谱结果，先初始化图谱或换一个关键词试试。" />
        </div>

        <svg v-else :viewBox="`0 0 ${canvasWidth} ${canvasHeight}`" class="graph-svg">
          <g v-for="edge in edgeLines" :key="`${edge.source}-${edge.target}-${edge.label}`">
            <line
              :x1="edge.x1"
              :y1="edge.y1"
              :x2="edge.x2"
              :y2="edge.y2"
              class="edge-line"
            />
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
              :r="selectedNode?.id === node.id ? 28 : 24"
              :fill="node.color"
            />
            <text :x="node.x" :y="node.y + 5" class="node-text">
              {{ displayName(node) }}
            </text>
          </g>
        </svg>
      </el-card>

      <el-card class="detail-card">
        <template #header>
          <div class="card-header">
            <span>节点详情</span>
            <el-tag v-if="selectedNode" effect="plain">{{ selectedNode.label }}</el-tag>
          </div>
        </template>

        <el-empty v-if="!selectedNode" description="点击左侧节点查看属性详情。" />

        <div v-else class="detail-content">
          <h3>{{ displayName(selectedNode) }}</h3>
          <div class="property-list">
            <div v-for="([key, value]) in entries(selectedNode.properties)" :key="key" class="property-item">
              <span class="property-key">{{ key }}</span>
              <span class="property-value">{{ formatValue(value) }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import { knowledgeGraphAPI, type KnowledgeGraphResponse, type KnowledgeGraphNode } from '@/api'

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

const canvasWidth = 980
const canvasHeight = 620

const loading = ref(false)
const searchKeyword = ref('')
const graphData = ref<KnowledgeGraphResponse>({ nodes: [], edges: [] })
const selectedNode = ref<PositionedNode | null>(null)

const labelColor = (label: string) => {
  const palette: Record<string, string> = {
    LitchiVariety: '#2563eb',
    Disease: '#dc2626',
    Pest: '#f97316',
    Pesticide: '#16a34a',
    CultivationTechnique: '#7c3aed'
  }

  return palette[label] ?? '#475569'
}

const positionedNodes = computed<PositionedNode[]>(() => {
  const nodes = graphData.value.nodes
  if (!nodes.length) {
    return []
  }

  const radius = Math.max(160, Math.min(canvasWidth, canvasHeight) / 2 - 90)
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
  if (typeof name === 'string' && name.trim()) {
    return name
  }
  return node.label
}

const entries = (properties: Record<string, unknown>) => Object.entries(properties)

const formatValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return value.join(', ')
  }

  if (value && typeof value === 'object') {
    return JSON.stringify(value)
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
    ElMessage.error('加载知识图谱失败，请检查后端和 Neo4j 服务。')
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

onMounted(() => {
  loadGraphData()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 28px;
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.toolbar {
  display: flex;
  gap: 12px;
}

.content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.graph-card,
.detail-card {
  border-radius: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.empty-wrapper {
  min-height: 620px;
  display: grid;
  place-items: center;
}

.graph-svg {
  width: 100%;
  min-height: 620px;
  background:
    radial-gradient(circle at center, rgba(37, 99, 235, 0.06), transparent 58%),
    #f8fafc;
  border-radius: 20px;
}

.edge-line {
  stroke: rgba(148, 163, 184, 0.88);
  stroke-width: 2;
}

.edge-label {
  fill: #475569;
  font-size: 12px;
  text-anchor: middle;
}

.node {
  cursor: pointer;
}

.node-text {
  fill: white;
  font-size: 12px;
  text-anchor: middle;
  pointer-events: none;
}

.detail-content h3 {
  margin: 0 0 16px;
}

.property-list {
  display: grid;
  gap: 10px;
}

.property-item {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
}

.property-key {
  font-size: 12px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.property-value {
  color: #1f2937;
  word-break: break-word;
}

@media (max-width: 1180px) {
  .page {
    padding: 16px;
  }

  .content {
    grid-template-columns: 1fr;
  }
}
</style>
