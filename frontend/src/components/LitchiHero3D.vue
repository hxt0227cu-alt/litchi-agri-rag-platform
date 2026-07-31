<template>
  <div class="hero-3d" @pointerup="stopDrag" @pointerleave="stopDrag" @pointercancel="stopDrag">
    <div
      ref="stageRef"
      class="hero-stage"
      :class="{ fallback: !webglAvailable }"
      @pointerdown="startDrag"
      @pointermove="moveDrag"
    >
      <canvas v-if="webglAvailable" ref="canvasRef" class="hero-canvas" />

      <div v-else class="fallback-scene">
        <div class="fallback-litchi">
          <div class="fallback-leaf leaf-left"></div>
          <div class="fallback-leaf leaf-right"></div>
          <div class="fallback-stem"></div>
          <div class="fallback-fruit"></div>
        </div>
      </div>

      <button
        v-for="hotspot in hotspots"
        :key="hotspot.id"
        type="button"
        class="hotspot"
        :class="{ active: hotspot.id === activeHotspot.id }"
        :style="{ left: hotspot.left, top: hotspot.top }"
        @pointerenter="setActiveHotspot(hotspot)"
        @focus="setActiveHotspot(hotspot)"
        @click="selectHotspot(hotspot)"
      >
        <span class="hotspot-dot"></span>
        <span class="hotspot-label">{{ hotspot.label }}</span>
      </button>
    </div>

    <article class="hotspot-card" aria-live="polite">
      <strong>{{ activeHotspot.label }}</strong>
      <p>{{ activeHotspot.description }}</p>
    </article>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

let THREE: any = null

type Hotspot = {
  id: 'diagnosis' | 'chat' | 'training' | 'solutions'
  label: string
  description: string
  path: string
  left: string
  top: string
}

const props = withDefaults(
  defineProps<{
    interactive?: boolean
  }>(),
  {
    interactive: false
  }
)

const emit = defineEmits<{
  navigate: [path: string]
}>()

const hotspots: Hotspot[] = [
  {
    id: 'diagnosis',
    label: '病害识别',
    description: '上传图片或选择样图后快速判断病症，并把识别出的病症标签直接带入解决方案页。',
    path: '/diagnosis',
    left: '14%',
    top: '22%'
  },
  {
    id: 'chat',
    label: '智能问答',
    description: '结合知识文档和图谱回答农户问题，并引导进入病症确认和门店方案推荐。',
    path: '/chat',
    left: '74%',
    top: '18%'
  },
  {
    id: 'training',
    label: '学习课堂',
    description: '先补病症入门、拍照要点和安全边界，再进入识别、问答和方案选择。',
    path: '/training',
    left: '18%',
    top: '72%'
  },
  {
    id: 'solutions',
    label: '解决方案',
    description: '统一查看门店方案、风险提醒、门店信息和推荐原因，再决定是否提交求助。',
    path: '/solutions',
    left: '72%',
    top: '70%'
  }
]

const stageRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const activeHotspot = ref<Hotspot>(hotspots[0]!)
const webglAvailable = ref(false)

type RendererLike = {
  setSize: (width: number, height: number, updateStyle?: boolean) => void
  setPixelRatio: (ratio: number) => void
  render: (scene: any, camera: any) => void
  dispose: () => void
}

type SceneLike = {
  add: (...items: any[]) => void
  clear: () => void
}

type CameraLike = {
  aspect: number
  updateProjectionMatrix: () => void
  position: {
    set: (x: number, y: number, z: number) => void
  }
}

type GroupLike = {
  add: (...items: any[]) => void
  rotation: {
    x: number
    y: number
  }
}

let renderer: RendererLike | null = null
let scene: SceneLike | null = null
let camera: CameraLike | null = null
let litchiGroup: GroupLike | null = null
let animationFrame = 0
let resizeObserver: ResizeObserver | null = null
let isDragging = false
let dragX = 0
let dragY = 0
let hoverFrame = 0
let pendingHotspot: Hotspot | null = null

const canUseWebGL = () => {
  if (typeof window === 'undefined' || !window.WebGLRenderingContext) {
    return false
  }
  const canvas = document.createElement('canvas')
  return Boolean(canvas.getContext('webgl') || canvas.getContext('experimental-webgl'))
}

const syncRendererSize = () => {
  if (!renderer || !camera || !stageRef.value) {
    return
  }
  const { clientWidth, clientHeight } = stageRef.value
  renderer.setSize(clientWidth, clientHeight, false)
  camera.aspect = clientWidth / clientHeight
  camera.updateProjectionMatrix()
}

const buildLitchiModel = () => {
  const group = new THREE.Group()

  const fruitGeometry = new THREE.SphereGeometry(1.08, 40, 40)
  const fruitMaterial = new THREE.MeshStandardMaterial({
    color: '#c94838',
    roughness: 0.72,
    metalness: 0.08
  })
  const fruit = new THREE.Mesh(fruitGeometry, fruitMaterial)
  group.add(fruit)

  const stem = new THREE.Mesh(
    new THREE.CylinderGeometry(0.06, 0.09, 0.45, 12),
    new THREE.MeshStandardMaterial({ color: '#7a4b25', roughness: 0.92 })
  )
  stem.position.set(0, 1.2, 0)
  stem.rotation.z = -0.28
  group.add(stem)

  const leaf = new THREE.Mesh(
    new THREE.ShapeGeometry(
      new THREE.Shape([
        new THREE.Vector2(0, 0),
        new THREE.Vector2(0.35, 0.18),
        new THREE.Vector2(0.8, 0),
        new THREE.Vector2(0.35, -0.14)
      ])
    ),
    new THREE.MeshStandardMaterial({ color: '#4f7f48', side: THREE.DoubleSide, roughness: 0.88 })
  )
  leaf.position.set(0.25, 1.42, 0.05)
  leaf.rotation.x = -0.25
  leaf.rotation.y = 0.35
  group.add(leaf)

  const spikeGeometry = new THREE.SphereGeometry(0.075, 8, 8)
  const spikeMaterial = new THREE.MeshStandardMaterial({
    color: '#dc6a54',
    roughness: 0.55,
    metalness: 0.04
  })

  const spikeCount = 48
  for (let index = 0; index < spikeCount; index += 1) {
    const phi = Math.acos(1 - (2 * (index + 0.5)) / spikeCount)
    const theta = Math.PI * (1 + Math.sqrt(5)) * (index + 0.5)
    const spike = new THREE.Mesh(spikeGeometry, spikeMaterial)
    const radius = 1.14
    spike.position.set(
      radius * Math.sin(phi) * Math.cos(theta),
      radius * Math.cos(phi),
      radius * Math.sin(phi) * Math.sin(theta)
    )
    group.add(spike)
  }

  group.rotation.x = 0.28
  group.rotation.y = -0.32
  return group
}

const startScene = () => {
  if (!canvasRef.value || !stageRef.value) {
    return
  }

  const nextScene = new THREE.Scene()
  const nextCamera = new THREE.PerspectiveCamera(38, 1, 0.1, 100)
  nextCamera.position.set(0, 0.2, 4.6)

  const nextRenderer = new THREE.WebGLRenderer({
    canvas: canvasRef.value,
    alpha: true,
    antialias: true,
    powerPreference: 'high-performance'
  })
  nextRenderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))

  const ambientLight = new THREE.AmbientLight('#ffe6b7', 1.35)
  const keyLight = new THREE.DirectionalLight('#fff7ec', 1.8)
  keyLight.position.set(3, 4, 4)
  const fillLight = new THREE.PointLight('#ff9b7a', 1.1, 12)
  fillLight.position.set(-2.6, -1.2, 3.2)
  nextScene.add(ambientLight, keyLight, fillLight)

  const nextLitchiGroup = buildLitchiModel()
  nextScene.add(nextLitchiGroup)

  scene = nextScene
  camera = nextCamera
  renderer = nextRenderer
  litchiGroup = nextLitchiGroup

  const animate = () => {
    animationFrame = window.requestAnimationFrame(animate)
    if (litchiGroup && !isDragging) {
      litchiGroup.rotation.y += 0.006
    }
    if (scene && camera) {
      renderer?.render(scene, camera)
    }
  }

  syncRendererSize()
  animate()

  resizeObserver = new ResizeObserver(() => {
    syncRendererSize()
  })
  resizeObserver.observe(stageRef.value)
}

const startDrag = (event: PointerEvent) => {
  isDragging = true
  dragX = event.clientX
  dragY = event.clientY
}

const moveDrag = (event: PointerEvent) => {
  if (!isDragging || !litchiGroup) {
    return
  }

  const deltaX = event.clientX - dragX
  const deltaY = event.clientY - dragY
  litchiGroup.rotation.y += deltaX * 0.008
  litchiGroup.rotation.x += deltaY * 0.006
  dragX = event.clientX
  dragY = event.clientY
}

const stopDrag = () => {
  isDragging = false
}

const setActiveHotspot = (hotspot: Hotspot) => {
  if (hotspot.id === activeHotspot.value.id) {
    return
  }

  pendingHotspot = hotspot
  if (hoverFrame) {
    return
  }

  hoverFrame = window.requestAnimationFrame(() => {
    if (pendingHotspot && pendingHotspot.id !== activeHotspot.value.id) {
      activeHotspot.value = pendingHotspot
    }
    pendingHotspot = null
    hoverFrame = 0
  })
}

const selectHotspot = (hotspot: Hotspot) => {
  pendingHotspot = null
  if (hoverFrame) {
    window.cancelAnimationFrame(hoverFrame)
    hoverFrame = 0
  }
  activeHotspot.value = hotspot
  if (props.interactive) {
    emit('navigate', hotspot.path)
  }
}

onMounted(async () => {
  webglAvailable.value = canUseWebGL()
  if (webglAvailable.value) {
    THREE = await import('three')
    startScene()
  }
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationFrame)
  if (hoverFrame) {
    window.cancelAnimationFrame(hoverFrame)
  }
  resizeObserver?.disconnect()
  renderer?.dispose()
  scene?.clear()
  renderer = null
  scene = null
  camera = null
  litchiGroup = null
})
</script>

<style scoped>
.hero-3d {
  display: grid;
  gap: 16px;
}

.hero-stage {
  position: relative;
  min-height: 340px;
  border-radius: 30px;
  overflow: hidden;
  cursor: grab;
  contain: layout paint;
  background:
    radial-gradient(circle at 30% 20%, rgba(255, 233, 186, 0.86), transparent 24%),
    radial-gradient(circle at 72% 72%, rgba(255, 110, 78, 0.24), transparent 32%),
    linear-gradient(145deg, rgba(20, 53, 45, 0.98), rgba(11, 30, 26, 0.96));
  border: 1px solid rgba(255, 244, 212, 0.16);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.04),
    0 26px 48px rgba(6, 19, 15, 0.24);
}

.hero-stage:active {
  cursor: grabbing;
}

.hero-stage::before,
.hero-stage::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.hero-stage::before {
  inset: 16% 18%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 226, 182, 0.24), transparent 70%);
  filter: blur(24px);
}

.hero-stage::after {
  right: 8%;
  bottom: 10%;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 122, 91, 0.18), transparent 70%);
  filter: blur(20px);
}

.hero-canvas,
.fallback-scene {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  will-change: transform;
}

.fallback-scene {
  display: grid;
  place-items: center;
}

.fallback-litchi {
  position: relative;
  width: 220px;
  height: 220px;
}

.fallback-fruit {
  position: absolute;
  inset: 38px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 35% 30%, rgba(255, 214, 198, 0.8), transparent 18%),
    radial-gradient(circle at 60% 55%, rgba(171, 34, 27, 0.5), transparent 60%),
    linear-gradient(145deg, #d65a48, #a92a27);
  box-shadow:
    inset 0 -16px 28px rgba(96, 18, 15, 0.38),
    0 20px 40px rgba(0, 0, 0, 0.2);
}

.fallback-fruit::before {
  content: '';
  position: absolute;
  inset: 8px;
  border-radius: 50%;
  background-image: radial-gradient(circle, rgba(255, 193, 176, 0.55) 0 2px, transparent 2px);
  background-size: 18px 18px;
  opacity: 0.4;
}

.fallback-stem {
  position: absolute;
  left: 106px;
  top: 22px;
  width: 8px;
  height: 44px;
  border-radius: 999px;
  background: linear-gradient(180deg, #8a5a2b, #69431f);
  transform: rotate(-20deg);
}

.fallback-leaf {
  position: absolute;
  top: 6px;
  width: 86px;
  height: 40px;
  border-radius: 90% 10% 90% 10%;
  background: linear-gradient(135deg, #6fb567, #3f7f41);
  box-shadow: inset 0 -10px 16px rgba(14, 59, 24, 0.28);
}

.leaf-left {
  left: 30px;
  transform: rotate(-12deg);
}

.leaf-right {
  right: 24px;
  transform: scaleX(-1) rotate(-18deg);
}

.hotspot {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid rgba(255, 244, 212, 0.14);
  border-radius: 999px;
  background: rgba(17, 34, 29, 0.72);
  backdrop-filter: blur(12px);
  color: #fff4d4;
  cursor: pointer;
  will-change: transform;
  transition:
    transform 0.22s ease,
    background 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.hotspot:hover,
.hotspot.active {
  transform: translateY(-2px);
  background: rgba(242, 140, 40, 0.2);
  box-shadow: 0 14px 32px rgba(0, 0, 0, 0.18);
  border-color: rgba(255, 210, 111, 0.24);
}

.hotspot-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ffd26f;
  box-shadow: 0 0 0 6px rgba(255, 210, 111, 0.12);
  animation: hotspot-pulse 2.4s ease-in-out infinite;
}

.hotspot-label {
  font-size: 13px;
  font-weight: 700;
}

.hotspot-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 250, 242, 0.96), rgba(255, 245, 233, 0.96));
  border: 1px solid rgba(34, 53, 47, 0.08);
  box-shadow: 0 18px 42px rgba(35, 54, 45, 0.08);
  contain: layout paint;
  will-change: transform, opacity;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.hotspot-card strong {
  color: var(--ink-strong);
  font-size: 18px;
}

.hotspot-card p {
  margin: 10px 0 0;
  color: var(--ink-soft);
  line-height: 1.75;
}

@keyframes hotspot-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 6px rgba(255, 210, 111, 0.12);
  }

  50% {
    box-shadow: 0 0 0 10px rgba(255, 210, 111, 0.05);
  }
}

@media (max-width: 720px) {
  .hero-stage {
    min-height: 300px;
  }

  .hotspot {
    padding: 8px 12px;
  }

  .hotspot-label {
    font-size: 12px;
  }
}
</style>
