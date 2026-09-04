import type { PlatformRole } from '../types/platform.js'

export type NavIconKey =
  | 'home'
  | 'reading'
  | 'picture'
  | 'chat'
  | 'agent'
  | 'orchard'
  | 'files'
  | 'document'
  | 'edit'
  | 'shop'
  | 'tickets'
  | 'user'
  | 'bell'
  | 'trend'
  | 'analysis'
  | 'folder'
  | 'connection'
  | 'setting'

export type RoleNavItem = {
  path: string
  label: string
  icon: NavIconKey
}

export type PageMeta = {
  title: string
  subtitle: string
}

export const DEFAULT_ROUTE_BY_ROLE: Record<PlatformRole, string> = {
  farmer: '/farmer/workbench',
  shopkeeper: '/shop/workbench',
  technician: '/technician/workbench'
}

export const ROLE_NAV_ITEMS: Record<PlatformRole, RoleNavItem[]> = {
  farmer: [
    { path: '/farmer/workbench', label: '农户工作台', icon: 'home' },
    { path: '/training', label: '学习课堂', icon: 'reading' },
    { path: '/diagnosis', label: '病害识别', icon: 'picture' },
    { path: '/chat', label: '智能问答', icon: 'chat' },
    { path: '/agent', label: '任务智能体', icon: 'agent' },
    { path: '/orchards', label: '我的果园', icon: 'orchard' },
    { path: '/solutions', label: '解决方案', icon: 'files' },
    { path: '/consultations/my', label: '我的求助', icon: 'document' },
    { path: '/feedback', label: '满意度反馈', icon: 'edit' }
  ],
  shopkeeper: [
    { path: '/shop/workbench', label: '门店工作台', icon: 'shop' },
    { path: '/shop/plans', label: '配药方案', icon: 'tickets' },
    { path: '/shop/profile', label: '店铺管理', icon: 'user' },
    { path: '/shop/inbox', label: '待处理求助', icon: 'bell' },
    { path: '/shop/trends', label: '高频病症', icon: 'trend' }
  ],
  technician: [
    { path: '/technician/workbench', label: '管理员工作台', icon: 'home' },
    { path: '/agent', label: '任务智能体', icon: 'agent' },
    { path: '/orchards', label: '果园档案', icon: 'orchard' },
    { path: '/evaluation', label: '评测中心', icon: 'analysis' },
    { path: '/document', label: '知识文档', icon: 'folder' },
    { path: '/knowledge', label: '研判图谱', icon: 'connection' },
    { path: '/system', label: '系统状态', icon: 'setting' }
  ]
}

export const PAGE_META: Record<string, PageMeta> = {
  '/farmer/workbench': {
    title: '农户工作台',
    subtitle: '把学习、识别、问答、方案选择、求助提交和满意度反馈收成一条清晰的农户协同链路。'
  },
  '/shop/workbench': {
    title: '门店工作台',
    subtitle: '围绕店铺资料、方案供给、待处理求助和高频病症看板，组织门店侧日常协同。'
  },
  '/technician/workbench': {
    title: '管理员工作台',
    subtitle: '集中查看服务健康、文档索引、自动评分和协同推荐运行情况，持续优化 AI 工程表现。'
  },
  '/training': {
    title: '农户学习课堂',
    subtitle: '先学会判断病症、拍照识别和安全边界，再进入智能问答与门店方案推荐。'
  },
  '/diagnosis': {
    title: '病害识别',
    subtitle: '上传病斑图片或使用样图完成识别，并把识别出的病症标签直接带入解决方案页。'
  },
  '/chat': {
    title: '智能问答',
    subtitle: '结合知识文档和研判图谱生成可追溯回答，并引导农户进入病症确认与方案推荐。'
  },
  '/agent': {
    title: '任务智能体',
    subtitle: '把复杂农技问题拆成几步分析，再汇总知识库、图谱与门店方案作为依据。'
  },
  '/orchards': {
    title: '果园档案',
    subtitle: '维护品种、生育期和位置信息，让诊断与智能分析更贴合自家果园。'
  },
  '/solutions': {
    title: '解决方案',
    subtitle: '围绕病症标签推荐门店方案，展示摘要、风险提醒、门店信息和推荐原因。'
  },
  '/consultations/my': {
    title: '我的求助',
    subtitle: '查看已提交给门店的求助记录、当前状态、目标门店和所选方案。'
  },
  '/feedback': {
    title: '满意度反馈',
    subtitle: '记录农户对识别、问答和方案推荐链路的满意度，帮助持续优化体验。'
  },
  '/shop/profile': {
    title: '店铺管理',
    subtitle: '维护门店基础信息、服务范围和擅长方向，作为推荐卡和求助协同的可信底座。'
  },
  '/shop/plans': {
    title: '配药方案',
    subtitle: '维护门店方案库，统一配置病症、阶段、摘要、风险提醒和库存状态。'
  },
  '/shop/inbox': {
    title: '待处理求助',
    subtitle: '查看农户提交的求助意向，按待处理、已联系、已完成推进门店协同。'
  },
  '/shop/trends': {
    title: '高频病症',
    subtitle: '根据农户求助意向聚合近期高频病症和热度变化，帮助门店提前准备。'
  },
  '/knowledge': {
    title: '研判图谱',
    subtitle: '展示荔枝品种、病虫害、药剂和管理技术之间的关联关系，既能辅助技术研判，也能服务农户课堂学习。'
  },
  '/document': {
    title: '知识文档',
    subtitle: '由管理员统一维护知识来源，为问答、评测和规则优化提供可信依据。'
  },
  '/system': {
    title: '系统状态',
    subtitle: '查看服务健康、运行参数、初始化策略和底层依赖的可用状态。'
  },
  '/evaluation': {
    title: '评测中心',
    subtitle: '以自动量表评分为主、人工复核为辅，持续发现低分问题并推动优化。'
  },
  '/history': {
    title: '咨询记录',
    subtitle: '查看历史问答记录与来源追溯，作为我的求助之外的辅助参考。'
  }
}

export const DISEASE_TAG_OPTIONS = ['炭疽病', '霜疫霉病', '蒂蛀虫', '红蜘蛛', '雨季综合管理']

export const CONSULTATION_STATUS_OPTIONS = [
  { value: 'pending', label: '待处理' },
  { value: 'contacted', label: '已联系' },
  { value: 'completed', label: '已完成' }
]

export const REMEDY_INVENTORY_OPTIONS = ['有现货', '预订可配', '缺货']
