import { formatToday } from '@/utils/auth.js'

const REMINDER_SETTINGS_KEY = 'nutrimind_reminder_settings'
const REMINDER_POPUP_KEY = 'nutrimind_reminder_popup'

const DEFAULT_REMINDER_SETTINGS = {
  enabled: true,
  popupEnabled: true,
  mealRecordReminder: true,
  nutritionAnalysisReminder: true,
  executionSuggestionReminder: true
}

function normalizeSettings(value) {
  return {
    ...DEFAULT_REMINDER_SETTINGS,
    ...(value || {})
  }
}

export function getReminderSettings() {
  return normalizeSettings(uni.getStorageSync(REMINDER_SETTINGS_KEY) || {})
}

export function saveReminderSettings(settings) {
  const normalized = normalizeSettings(settings)
  uni.setStorageSync(REMINDER_SETTINGS_KEY, normalized)
  return normalized
}

function numberValue(value) {
  const parsed = Number(value || 0)
  return Number.isFinite(parsed) ? parsed : 0
}

function clampPercent(value) {
  if (!Number.isFinite(value)) return 0
  return Math.max(0, Math.min(999, Math.round(value)))
}

function buildMealTypeMap(details = []) {
  return details.reduce((accumulator, item) => {
    const type = item?.mealType || 'SNACK'
    accumulator[type] = accumulator[type] || []
    accumulator[type].push(item)
    return accumulator
  }, {})
}

function buildEvidence({ userFacts = [], graphFacts = [], sourceFacts = [] } = {}) {
  return { userFacts, graphFacts, sourceFacts }
}

function createReminder(base, patch) {
  return {
    level: 'normal',
    badge: '主动提醒',
    actionLabel: '立即查看',
    actionUrl: '/pages/meals/index',
    evidence: buildEvidence(),
    ...base,
    ...patch
  }
}

export function buildSmartReminders({ profile, dailyRecord, weeklyReport, now = new Date() }) {
  const settings = getReminderSettings()
  if (!settings.enabled) return []

  const details = Array.isArray(dailyRecord?.details) ? dailyRecord.details : []
  const grouped = buildMealTypeMap(details)
  const hour = now.getHours()
  const reminders = []

  const targetCalories = numberValue(profile?.healthGoal?.targetCalories)
  const targetProtein = numberValue(profile?.healthGoal?.targetProtein)
  const totalCalories = numberValue(dailyRecord?.totalCalories)
  const totalProtein = numberValue(dailyRecord?.totalProtein)
  const recordedDays = numberValue(weeklyReport?.recordedDays)

  if (settings.mealRecordReminder) {
    if (hour >= 10 && !grouped.BREAKFAST?.length) {
      reminders.push(createReminder({}, {
        id: 'missing-breakfast',
        level: 'high',
        badge: '记录监测 Agent',
        title: '你今天还没有记录早餐',
        description: '早餐缺失会影响全天能量分配，建议先补记早餐或补充一份轻早餐。',
        actionLabel: '去补记',
        actionUrl: '/pages/meals/index',
        evidence: buildEvidence({
          userFacts: [
            `当前时间已过 ${String(hour).padStart(2, '0')}:00`,
            `今天早餐记录次数：${grouped.BREAKFAST?.length || 0}`,
            `今天总记录条数：${details.length}`
          ],
          graphFacts: [
            'GraphRAG 关系显示规律早餐有助于稳定全天能量分配',
            '缺失早餐记录会影响后续饮食偏差分析',
            '执行 Agent 建议先补记或补充一份轻早餐'
          ],
          sourceFacts: [
            '《中国居民膳食指南》建议规律进餐',
            '权威营养建议强调避免长期漏餐'
          ]
        })
      }))
    }

    if (hour >= 14 && !grouped.LUNCH?.length) {
      reminders.push(createReminder({}, {
        id: 'missing-lunch',
        level: 'high',
        badge: '记录监测 Agent',
        title: '午餐记录还空着',
        description: '系统还无法判断你下午的摄入状态，补记午餐后会得到更准确的分析。',
        actionLabel: '记录午餐',
        actionUrl: '/pages/meals/index',
        evidence: buildEvidence({
          userFacts: [
            `当前时间已过 ${String(hour).padStart(2, '0')}:00`,
            `今天午餐记录次数：${grouped.LUNCH?.length || 0}`,
            `当前已记录餐次：${Object.keys(grouped).length}`
          ],
          graphFacts: [
            'GraphRAG 关系显示午餐是白天主要能量补充节点',
            '午餐缺失会降低后续计划生成准确度',
            '系统建议优先补记午餐，再继续分析'
          ],
          sourceFacts: [
            '《中国居民膳食指南》建议三餐规律分配',
            '营养管理建议优先保证白天主要餐次完整'
          ]
        })
      }))
    }

    if (hour >= 20 && !grouped.DINNER?.length) {
      reminders.push(createReminder({}, {
        id: 'missing-dinner',
        level: 'medium',
        badge: '执行提醒 Agent',
        title: '今晚还没有形成晚餐记录',
        description: '如果你已经吃过晚餐，可以补记；如果还没吃，建议优先选择清淡高蛋白组合。',
        actionLabel: '去记录',
        actionUrl: '/pages/meals/index',
        evidence: buildEvidence({
          userFacts: [
            `当前时间已过 ${String(hour).padStart(2, '0')}:00`,
            `今天晚餐记录次数：${grouped.DINNER?.length || 0}`,
            `当前总热量：${totalCalories} kcal`
          ],
          graphFacts: [
            'GraphRAG 关系显示晚餐结构会明显影响全天热量判断',
            '清淡高蛋白组合更适合作为晚间补充',
            '执行 Agent 可继续生成晚餐修正建议'
          ],
          sourceFacts: [
            '健康饮食建议强调晚餐不过量、不过晚',
            '减脂和控糖场景通常更关注晚餐结构'
          ]
        })
      }))
    }
  }

  if (settings.nutritionAnalysisReminder) {
    if (targetCalories > 0 && totalCalories > targetCalories * 1.1) {
      reminders.push(createReminder({}, {
        id: 'calories-high',
        level: 'high',
        badge: '分析 Agent',
        title: '今日热量已经偏高',
        description: `当前摄入约为目标的 ${clampPercent((totalCalories / targetCalories) * 100)}%，建议下一餐减少高油脂和高糖食物。`,
        actionLabel: '查看建议',
        actionUrl: '/pages/advisor/index',
        evidence: buildEvidence({
          userFacts: [
            `当前热量：${totalCalories} kcal`,
            `目标热量：${targetCalories} kcal`,
            `完成度：${clampPercent((totalCalories / targetCalories) * 100)}%`
          ],
          graphFacts: [
            'GraphRAG 关系命中“高油脂食物需要优先控制”',
            '当前饮食状态与控热量目标出现偏离',
            '分析 Agent 建议通过下一餐做结构性修正'
          ],
          sourceFacts: [
            '《中国居民膳食指南》建议控制高油高糖饮食频率',
            '权威营养资料强调总能量平衡'
          ]
        })
      }))
    } else if (targetCalories > 0 && hour >= 18 && totalCalories < targetCalories * 0.45) {
      reminders.push(createReminder({}, {
        id: 'calories-low',
        level: 'medium',
        badge: '分析 Agent',
        title: '当前摄入偏低，别把自己饿过头',
        description: '系统判断你今天整体摄入偏低，晚餐建议补充主食和优质蛋白，避免后续暴食。',
        actionLabel: '生成建议',
        actionUrl: '/pages/meals/plan',
        evidence: buildEvidence({
          userFacts: [
            `当前热量：${totalCalories} kcal`,
            `目标热量：${targetCalories} kcal`,
            `当前时间：${String(hour).padStart(2, '0')}:00`
          ],
          graphFacts: [
            'GraphRAG 关系显示总摄入明显偏低时更容易出现补偿性进食',
            '执行 Agent 建议晚餐补充主食和优质蛋白',
            '系统会优先推荐更稳妥的补能组合'
          ],
          sourceFacts: [
            '营养管理建议避免极端低摄入',
            '规律补充能量有助于维持代谢和饱腹感'
          ]
        })
      }))
    }

    if (targetProtein > 0 && hour >= 18 && totalProtein < targetProtein * 0.55) {
      reminders.push(createReminder({}, {
        id: 'protein-low',
        level: 'medium',
        badge: 'GraphRAG Agent',
        title: '蛋白质摄入还不够',
        description: '结合你的目标和今日记录，系统建议晚餐优先补充鸡胸肉、鱼类、鸡蛋或豆制品。',
        actionLabel: '查看替代',
        actionUrl: '/pages/advisor/index',
        evidence: buildEvidence({
          userFacts: [
            `当前蛋白质：${totalProtein} g`,
            `目标蛋白质：${targetProtein} g`,
            `完成度：${clampPercent((totalProtein / targetProtein) * 100)}%`
          ],
          graphFacts: [
            'GraphRAG 关系命中鸡胸肉、鱼类、鸡蛋和豆制品等替代来源',
            '减脂和增肌目标都需要优先保证优质蛋白',
            '分析 Agent 判断晚餐是本次补充蛋白的最佳窗口'
          ],
          sourceFacts: [
            '权威营养资料强调优质蛋白对饱腹感和瘦体重维持的重要性',
            '膳食建议鼓励分餐次补充蛋白质'
          ]
        })
      }))
    }
  }

  if (settings.executionSuggestionReminder && recordedDays > 0 && recordedDays < 3) {
    reminders.push(createReminder({}, {
      id: 'weekly-consistency',
      level: 'low',
      badge: '执行 Agent',
      title: '本周记录频率还可以再稳定一些',
      description: '连续记录越完整，多 Agent 分析和饮食计划就越贴近你的真实状态。',
      actionLabel: '继续记录',
      actionUrl: '/pages/capture/index',
      evidence: buildEvidence({
        userFacts: [
          `本周已记录天数：${recordedDays}`,
          `今日记录条数：${details.length}`
        ],
        graphFacts: [
          'GraphRAG 和多 Agent 都依赖稳定的连续数据',
          '连续记录越完整，异常识别和计划生成越贴近真实状态',
          '执行 Agent 建议继续保持记录节奏'
        ],
        sourceFacts: [
          '长期记录比单日记录更能反映饮食模式',
          '营养干预通常依赖连续观察数据'
        ]
      })
    }))
  }

  return reminders.slice(0, 4)
}

export function buildReminderDigest({ profile, dailyRecord, weeklyReport, now = new Date() }) {
  const settings = getReminderSettings()
  if (!settings.enabled) return null

  const reminders = buildSmartReminders({ profile, dailyRecord, weeklyReport, now })
  if (!reminders.length) {
    return {
      title: '今天的记录状态不错',
      description: '继续保持当前节奏，系统会根据新的记录持续优化建议。',
      level: 'good'
    }
  }

  const top = reminders[0]
  return {
    title: top.title,
    description: top.description,
    level: top.level
  }
}

export function shouldShowReminderPopup(reminders, settings = getReminderSettings()) {
  if (!settings.enabled || !settings.popupEnabled || !reminders.length) return false
  const top = reminders[0]
  if (!top || top.level === 'low') return false

  const today = formatToday()
  const popupState = uni.getStorageSync(REMINDER_POPUP_KEY) || {}
  const popupKey = `${today}:${top.id}`
  return popupState.key !== popupKey
}

export function markReminderPopupShown(reminderId) {
  uni.setStorageSync(REMINDER_POPUP_KEY, {
    key: `${formatToday()}:${reminderId}`,
    shownAt: Date.now()
  })
}
