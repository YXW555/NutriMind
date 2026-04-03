export const dashboardSummary = {
  overview: [
    { label: '知识条目', value: '21', hint: '已接入权威来源' },
    { label: '食物条目', value: '128', hint: '支持图像识别匹配' },
    { label: '待审核内容', value: '6', hint: '社区帖子与评论' },
    { label: '系统可用率', value: '99.2%', hint: '近 7 日观测' }
  ],
  highlights: [
    'RAG 知识库已接入中国营养学会、国家卫健委、WHO 等权威来源',
    'Agent 饮食计划支持规则兜底与知识引用',
    '图像识别结果可以回流到食物库维护流程'
  ],
  recentActions: [
    { id: 'act-1', title: '新增 WHO 健康膳食条目', time: '2 分钟前', owner: '系统管理员' },
    { id: 'act-2', title: '修正无糖酸奶营养数据', time: '18 分钟前', owner: '食物库维护员' },
    { id: 'act-3', title: '拦截 1 条高风险评论', time: '36 分钟前', owner: '社区审核员' }
  ]
}

export const knowledgeItems = [
  {
    id: 'kb-001',
    title: '规律进餐与餐次安排',
    authority: '中国营养学会',
    sourceName: '中国居民膳食指南（2022）准则六',
    sourceUrl: 'https://dg.cnsoc.org/article/2021b.html',
    tag: '饮食结构',
    status: '已启用',
    excerpt: '规律进餐、合理分配热量和蛋白质，比极端节食更利于长期执行。'
  },
  {
    id: 'kb-002',
    title: '体重管理与成人肥胖食养原则',
    authority: '国家卫生健康委',
    sourceName: '成人肥胖食养指南（2024年版）解读',
    sourceUrl: 'https://www.nhc.gov.cn/sps/s7887k/202402/34b8f96ee0ad47c6b7ec34f0201b745c.shtml',
    tag: '体重管理',
    status: '已启用',
    excerpt: '体重管理强调循序渐进和可持续，而非依赖极端节食。'
  },
  {
    id: 'kb-003',
    title: '健康膳食核心原则（WHO）',
    authority: '世界卫生组织',
    sourceName: 'WHO Healthy diet',
    sourceUrl: 'https://www.who.int/news-room/fact-sheets/detail/healthy-diet',
    tag: '权威共识',
    status: '已启用',
    excerpt: '蔬果摄入、加工食品控制、糖盐脂约束是全球通用的健康饮食原则。'
  },
  {
    id: 'kb-004',
    title: '健康饮食模式与食物密度',
    authority: 'USDA / HHS',
    sourceName: 'Dietary Guidelines for Americans 2020-2025',
    sourceUrl: 'https://www.dietaryguidelines.gov/',
    tag: '国际指南',
    status: '待复核',
    excerpt: '建议围绕整体饮食模式和营养密度高的食物进行默认推荐。'
  }
]

export const foodItems = [
  { id: 101, name: '鸡胸肉', category: '高蛋白', calories: 165, protein: 31, status: '启用' },
  { id: 102, name: '燕麦片', category: '主食', calories: 389, protein: 16.9, status: '启用' },
  { id: 103, name: '西兰花', category: '蔬菜', calories: 34, protein: 2.8, status: '启用' },
  { id: 104, name: '无糖酸奶', category: '乳制品', calories: 72, protein: 2.5, status: '待校对' },
  { id: 105, name: '三文鱼', category: '高蛋白', calories: 208, protein: 20.4, status: '启用' },
  { id: 106, name: '糙米饭', category: '主食', calories: 111, protein: 2.6, status: '待校对' }
]

export const moderationItems = [
  {
    id: 'post-01',
    type: '社区帖子',
    author: '小白减脂中',
    risk: '低',
    reason: '内容正常，建议通过',
    status: '待审核',
    contentPreview: '分享今天的减脂午餐和热量控制思路。'
  },
  {
    id: 'post-02',
    type: '社区图片',
    author: '健身阿豪',
    risk: '中',
    reason: '图片与正文相关性待确认',
    status: '待审核',
    contentPreview: '上传了训练后的晚餐图片，待确认图文一致性。'
  },
  {
    id: 'comment-03',
    type: '评论',
    author: '路人甲',
    risk: '高',
    reason: '存在不友善表达，建议拦截',
    status: '待审核',
    contentPreview: '评论内容需要进一步人工复核。'
  }
]

export const systemCards = [
  { label: 'Gateway', status: '正常', detail: 'http://localhost:8080/api' },
  { label: 'RAG 检索', status: '正常', detail: 'Milvus + 本地检索兜底' },
  { label: 'Agent 计划生成', status: '正常', detail: '规则兜底已启用' },
  { label: '图像识别', status: '待检查', detail: '请确认 Python 推理服务状态' }
]

export const systemSettings = {
  ragEnabled: true,
  agentFallbackEnabled: true,
  visionEngine: 'mock',
  apiBase: 'http://localhost:8080/api'
}

export const systemLogs = [
  { id: 'log-1', level: 'INFO', message: '知识库已加载 21 个 chunk，支持权威来源引用。', time: '15:20:11' },
  { id: 'log-2', level: 'INFO', message: 'Agent 饮食计划生成服务可用，规则兜底已启用。', time: '15:22:43' },
  { id: 'log-3', level: 'WARN', message: '图像识别服务当前处于 mock 模式，建议答辩前切换并验证。', time: '15:24:58' }
]
