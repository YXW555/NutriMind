import { clearSession, getToken, openAuthPage } from './auth'
import { getApiBaseUrl } from './config'

const messageMap = {
  success: '操作成功',
  'register success': '注册成功',
  'login success': '登录成功',
  'meal saved': '饮食记录已保存',
  'meal detail deleted': '饮食明细已删除',
  'meal plan saved': '饮食计划已保存',
  'meal plan applied': '饮食计划已写入饮食记录',
  'meal plan not found': '未找到这一天的饮食计划',
  'meal plan is empty': '这一天的饮食计划还是空的',
  'meal plan can only be applied to an empty date': '只有当天还没有实际饮食记录时，才能一键应用计划',
  'health profile saved': '健康档案已保存',
  'health goal saved': '健康目标已保存',
  'weight log saved': '体重记录已保存',
  'weight log deleted': '体重记录已删除',
  'weight log not found': '未找到这条体重记录',
  'username already exists': '用户名已存在',
  'username is available': '用户名可用',
  'email already exists': '邮箱已被注册',
  'phone already exists': '手机号已被注册',
  'confirm password does not match': '两次输入的密码不一致',
  'username must be 4-20 characters and contain only letters, numbers, or underscores': '用户名需为 4-20 位字母、数字或下划线',
  'password must be 6-32 characters': '密码长度需在 6-32 位之间',
  'nickname must be at most 20 characters': '昵称最多 20 个字符',
  'email format is invalid': '邮箱格式不正确',
  'phone format is invalid': '手机号格式不正确',
  'username must not be blank': '请输入用户名',
  'password must not be blank': '请输入密码',
  'request validation failed': '请求参数校验失败',
  'invalid username or password': '账号或密码错误',
  'user not found': '用户不存在',
  'authentication failed': '登录校验失败，请重试',
  'cross-user access is not allowed': '不允许访问其他用户的数据',
  'database operation failed': '数据库写入失败，请检查后端服务和数据库配置',
  'register failed': '注册失败，请稍后重试',
  'internal server error': '服务器开小差了，请稍后再试',
  'meal-service failed': '饮食服务暂时不可用，请稍后再试',
  'ai-service failed': '智能识别服务暂时不可用，请稍后再试',
  'python inference service unavailable': '图像识别服务未启动，请先启动 Python 推理服务',
  'python inference returned no predictions': '这张图片没有识别出可靠结果，请换一张更清晰的图片再试',
  'failed to read uploaded image': '读取上传图片失败，请重新选择图片',
  'food recognition candidates unavailable': '暂时没有生成识别候选，请手动确认食物',
  'no image selected': '请先选择图片',
  'uploaded file must be an image': '请上传图片文件',
  '帖子不存在': '帖子不存在或已被删除',
  'post not found': '帖子不存在或已被删除',
  'comment not found': '评论不存在或已被删除',
  'comment created': '评论已发布',
  'comment deleted': '评论已删除',
  'post created': '发布成功',
  'favorite status updated': '收藏状态已更新',
  'like status updated': '点赞状态已更新',
  'image uploaded': '图片上传成功',
  'invalid image url': '图片地址不合法',
  'you can upload at most 3 images': '最多只能上传 3 张图片',
  'failed to serialize image urls': '图片数据处理失败',
  '食物不存在': '食物不存在或已被删除',
  'recognition success': '识别完成'
}

function normalizeMessage(message) {
  if (!message) {
    return ''
  }

  return messageMap[message] || message
}

function getJsonHeaders() {
  const headers = {
    'Content-Type': 'application/json'
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

function getUploadHeaders() {
  const headers = {}
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

function parseBody(body) {
  if (typeof body !== 'string') {
    return body
  }

  try {
    return JSON.parse(body)
  } catch (error) {
    return body
  }
}

function showError(message) {
  uni.showToast({
    title: normalizeMessage(message) || '请求失败',
    icon: 'none'
  })
}

function redirectToAuth() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const currentRoute = pages.length ? `/${pages[pages.length - 1].route}` : ''
  if (currentRoute === '/pages/auth/index') {
    return
  }

  setTimeout(() => {
    openAuthPage()
  }, 250)
}

function handleUnauthorized(message) {
  clearSession()
  showError(message || '登录已失效，请重新登录')
  redirectToAuth()
}

function handleBusinessResponse(statusCode, rawBody, resolve, reject) {
  const body = parseBody(rawBody)

  if (statusCode < 200 || statusCode >= 300) {
    if (statusCode === 401) {
      handleUnauthorized(body && body.msg)
    } else {
      showError(body && body.msg ? body.msg : `请求失败(${statusCode})`)
    }
    reject(body || { statusCode })
    return
  }

  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code')) {
    if (body.code === 200) {
      resolve(body.data)
      return
    }

    if (body.code === 401) {
      handleUnauthorized(body.msg)
    } else {
      showError(body.msg)
    }
    reject(body)
    return
  }

  resolve(body)
}

function request(url, method = 'GET', data = {}) {
  return new Promise((resolve, reject) => {
    const requestUrl = `${getApiBaseUrl()}${url}`
    uni.request({
      url: requestUrl,
      method,
      data,
      header: getJsonHeaders(),
      success: (res) => {
        handleBusinessResponse(res.statusCode, res.data, resolve, reject)
      },
      fail: (error) => {
        console.error('[NutriMind request failed]', {
          url: requestUrl,
          method,
          error
        })
        showError('网络异常，请稍后再试')
        reject(error)
      }
    })
  })
}

function resolveUploadPayload(options = {}) {
  const rawFile = options.file && options.file.file ? options.file.file : options.file
  const payload = {
    filePath: options.filePath,
    file: rawFile
  }

  if (!payload.filePath && rawFile && typeof rawFile === 'object') {
    payload.filePath = rawFile.path || rawFile.tempFilePath || ''
  }

  return payload
}

function upload(url, options = {}) {
  return new Promise((resolve, reject) => {
    const requestUrl = `${getApiBaseUrl()}${url}`
    const payload = resolveUploadPayload(options)
    const uploadTaskOptions = {
      url: requestUrl,
      name: options.name || 'file',
      header: getUploadHeaders(),
      formData: options.formData || {},
      success: (res) => {
        const statusCode = Number(res.statusCode || 0)
        handleBusinessResponse(statusCode, res.data, resolve, reject)
      },
      fail: (error) => {
        console.error('[NutriMind upload failed]', {
          url: requestUrl,
          error
        })
        showError('上传失败，请稍后再试')
        reject(error)
      }
    }

    const canUseBrowserFile = typeof window !== 'undefined'
      && typeof File !== 'undefined'
      && payload.file instanceof File

    if (canUseBrowserFile) {
      uploadTaskOptions.file = payload.file
    } else if (payload.filePath) {
      uploadTaskOptions.filePath = payload.filePath
    } else {
      showError('no image selected')
      reject(new Error('no image selected'))
      return
    }

    uni.uploadFile(uploadTaskOptions)
  })
}

export default {
  request,
  get(url, data) {
    return request(url, 'GET', data)
  },
  post(url, data) {
    return request(url, 'POST', data)
  },
  put(url, data) {
    return request(url, 'PUT', data)
  },
  delete(url, data) {
    return request(url, 'DELETE', data)
  },
  upload
}
