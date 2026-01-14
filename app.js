// app.js
const { Logger, LoadingManager, CacheManager, SecurityUtils, OfflineManager, PerformanceMonitor, globalStateManager, TokenManager } = require('./utils/index')
const PlatformUtils = require('./utils/platform-utils')

App({
  onLaunch() {
    // 初始化平台兼容性
    this.initPlatformCompatibility()
    
    // 初始化日志级别
    const envVersion = this.getEnvVersion()
    Logger.setLevel(envVersion === 'release' ? Logger.levels.ERROR : Logger.levels.DEBUG)
    Logger.info('App launched', { envVersion })

    this.globalData.envVersion = envVersion
    this.globalData.debug = envVersion !== 'release'
    this.globalData.baseUrl = this.getBaseUrl()
    this.globalData.platformInfo = PlatformUtils.getPlatformInfo()

    // 初始化各种管理器
    this.initStateManager()
    this.initOfflineManager()
    this.initPerformanceMonitor()
    this.initTokenManager()

    // 检查登录状态
    this.checkLoginStatus()

    // 清理过期缓存
    this.cleanExpiredCache()
  },

  initPlatformCompatibility() {
    const platformInfo = PlatformUtils.getPlatformInfo()
    const platformConfig = PlatformUtils.getPlatformConfig()
    
    Logger.info('Platform compatibility initialized', {
      platform: PlatformUtils.getPlatformDisplayName(),
      isHarmonyOS: platformInfo.isHarmonyOS,
      config: platformConfig
    })

    // 应用平台特定配置
    this.globalData.platformConfig = platformConfig
    
    // HarmonyOS特定处理
    if (platformInfo.isHarmonyOS) {
      Logger.info('HarmonyOS detected, applying compatibility measures')
      
      // 可以在这里添加HarmonyOS特定的初始化逻辑
      this.applyHarmonyOSOptimizations()
    }
  },

  applyHarmonyOSOptimizations() {
    // HarmonyOS特定优化
    Logger.info('Applying HarmonyOS optimizations')
    
    // 调整请求超时时间
    this.globalData.platformConfig.requestTimeout = 12000
    
    // 禁用某些可能不兼容的功能
    this.globalData.platformConfig.enableHapticFeedback = false
    
    // 可以添加更多HarmonyOS特定的优化
  },

  initStateManager() {
    // 恢复持久化状态
    globalStateManager.restoreState(['userPreferences', 'searchHistory'])
    
    // 监听用户信息变化
    globalStateManager.subscribe('userInfo', (newValue, oldValue) => {
      this.globalData.userInfo = newValue
      if (newValue) {
        Logger.info('User info updated', SecurityUtils.filterSensitiveData(newValue))
      }
    })

    // 监听登录状态变化
    globalStateManager.subscribe('isLoggedIn', (newValue) => {
      this.globalData.isLoggedIn = newValue
      if (!newValue) {
        this.clearUserData()
      }
    })
  },

  initOfflineManager() {
    // 启动网络监控
    OfflineManager.startNetworkMonitoring()
    
    // 尝试同步离线数据
    setTimeout(() => {
      OfflineManager.syncOfflineData()
    }, 2000) // 延迟2秒执行，确保应用完全启动
  },

  initTokenManager() {
    // 启动Token自动监控
    TokenManager.startTokenMonitoring()
    Logger.info('Token manager initialized')
  },

  initPerformanceMonitor() {
    // 延迟启动性能监控，避免启动时的错误
    setTimeout(() => {
      try {
        PerformanceMonitor.startMonitoring()
        Logger.info('Performance monitoring initialized')
      } catch (error) {
        Logger.error('Failed to initialize performance monitor', error)
      }
    }, 3000) // 延迟3秒启动
  },

  checkLoginStatus() {
    const token = wx.getStorageSync('token')
    const userInfo = wx.getStorageSync('userInfo')
    
    if (token && !SecurityUtils.isTokenExpired(token)) {
      this.globalData.token = token
      this.globalData.isLoggedIn = true
      this.globalData.userInfo = userInfo
      
      globalStateManager.setState('isLoggedIn', true)
      globalStateManager.setState('userInfo', userInfo)
      
      Logger.info('Login status restored')
    } else if (token) {
      Logger.warn('Token expired, clearing login data')
      this.logout()
    }
  },

  cleanExpiredCache() {
    try {
      const cacheInfo = CacheManager.getInfo()
      Logger.debug('Cache info on startup', cacheInfo)
    } catch (error) {
      Logger.error('Failed to get cache info', error)
    }
  },

  getEnvVersion() {
    try {
      const info = wx.getAccountInfoSync && wx.getAccountInfoSync()
      const v = info && info.miniProgram && info.miniProgram.envVersion
      return v || 'release'
    } catch (e) {
      Logger.error('Failed to get env version', e)
      return 'release'
    }
  },

  // 获取动态请求超时配置
  getRequestTimeout(url) {
    const platformConfig = this.globalData.platformConfig || {}
    const baseTimeout = platformConfig.requestTimeout || 10000
    
    const REQUEST_TIMEOUT = {
      default: baseTimeout,
      upload: baseTimeout * 3,     // 上传操作
      download: baseTimeout * 6,   // 下载操作
      export: baseTimeout * 4.5    // 导出操作
    }

    // 根据URL判断操作类型
    if (url.includes('/upload') || url.includes('/import')) {
      return REQUEST_TIMEOUT.upload
    }
    if (url.includes('/download') || url.includes('/file')) {
      return REQUEST_TIMEOUT.download
    }
    if (url.includes('/export')) {
      return REQUEST_TIMEOUT.export
    }
    
    return REQUEST_TIMEOUT.default
  },

  getBaseUrl() {
    const override = wx.getStorageSync('baseUrl')
    if (override) return override

    const envVersion = this.globalData.envVersion || this.getEnvVersion()
    const map = {
      develop: 'https://www.mkctj.cn/api',
      trial: 'https://www.mkctj.cn/api',
      release: 'https://www.mkctj.cn/api'
    }
    return map[envVersion] || 'https://www.mkctj.cn/api'
  },

  log() {
    if (this.globalData.debug) {
      const args = Array.prototype.slice.call(arguments)
      Logger.debug('App log', args)
    }
  },

  toCamelCaseKey(key) {
    if (!key || typeof key !== 'string') return key
    if (!key.includes('_')) return key
    return key.replace(/_([a-zA-Z0-9])/g, (_, c) => (c ? c.toUpperCase() : ''))
  },

  addCamelCaseAliases(value) {
    if (Array.isArray(value)) {
      return value.map(v => this.addCamelCaseAliases(v))
    }
    if (!value || typeof value !== 'object') {
      return value
    }

    const out = {}
    Object.keys(value).forEach((k) => {
      const v = this.addCamelCaseAliases(value[k])
      out[k] = v
      const camelKey = this.toCamelCaseKey(k)
      if (camelKey !== k && out[camelKey] === undefined) {
        out[camelKey] = v
      }
    })
    return out
  },

  globalData: {
    baseUrl: 'https://www.mkctj.cn/api',
    envVersion: 'release',
    debug: false,
    token: '',
    isLoggedIn: false,
    userInfo: null,
    platformInfo: null,
    platformConfig: null
  },

  // 封装请求方法 - 使用Token管理器和新的错误处理
  async request(options) {
    const that = this
    
    // 获取有效Token
    const token = await TokenManager.getValidToken() || ''
    
    Logger.debug('Making API request', { 
      url: options.url, 
      method: options.method || 'GET',
      hasToken: !!token 
    })

    return new Promise((resolve, reject) => {
      const requestOptions = {
        url: (that.globalData.baseUrl || that.getBaseUrl()) + options.url,
        method: options.method || 'GET',
        data: options.data || {},
        header: {
          'Content-Type': 'application/json',
          'Authorization': token
        },
        timeout: this.getRequestTimeout(options.url), // 动态超时配置
        success(res) {
          Logger.debug('API Response received', { 
            url: options.url, 
            statusCode: res.statusCode,
            dataType: typeof res.data
          })

          if (res.statusCode === 200) {
            const data = that.addCamelCaseAliases(res.data)
            // 处理两种格式：
            // 1. {code: 200, message: 'success', data: {...}}
            // 2. 直接返回数组 [...]
            if (Array.isArray(data)) {
              // 直接返回数组的情况（如 /finished-products）
              resolve({ code: 200, data: data })
            } else if (data.code === 200) {
              resolve(data)
            } else if (data.code === 500 && data.message && data.message.includes('登录')) {
              // 登录过期
              that.handleTokenExpired()
              reject(data)
            } else if (data.code) {
              reject(data)
            } else {
              // 其他情况，可能是分页数据 {content: [...], ...}
              resolve({ code: 200, data: data })
            }
          } else if (res.statusCode === 401) {
            // 未授权，跳转到登录页
            that.handleTokenExpired()
            reject(res)
          } else {
            Logger.error('API request failed with status', { 
              url: options.url, 
              statusCode: res.statusCode 
            })
            reject(res)
          }
        },
        fail(err) {
          Logger.error('API Request network error', { url: options.url, error: err })
          reject(err)
        }
      }

      wx.request(requestOptions)
    })
  },

  // 处理Token过期
  handleTokenExpired() {
    Logger.warn('Token expired, logging out user')
    this.clearUserData()
    globalStateManager.setState('isLoggedIn', false)
    globalStateManager.setState('userInfo', null)
    
    wx.redirectTo({ url: '/pages/login/login' })
  },

  // 清理用户数据
  clearUserData() {
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
    this.globalData.token = ''
    this.globalData.isLoggedIn = false
    this.globalData.userInfo = null
  },

  // 登录方法 - 增强安全性和错误处理
  login(username, password) {
    // 输入验证
    const cleanUsername = SecurityUtils.sanitizeInput(username)
    const passwordValidation = SecurityUtils.validatePassword(password)
    
    if (!SecurityUtils.validateUsername(cleanUsername)) {
      return Promise.reject({ message: '用户名格式不正确' })
    }
    
    if (!passwordValidation.isValid) {
      return Promise.reject({ message: passwordValidation.message })
    }

    Logger.info('User login attempt', { username: cleanUsername })

    return this.request({
      url: '/auth/login',
      method: 'POST',
      data: { username: cleanUsername, password }
    }).then(res => {
      // 后端返回: {code: 200, data: {token: 'Bearer xxx', user: {...}}}
      if (res.code === 200 && res.data && res.data.token) {
        const token = res.data.token
        const user = res.data.user || { username: cleanUsername }
        
        // 验证token格式
        if (SecurityUtils.isTokenExpired(token)) {
          Logger.error('Received expired token')
          return Promise.reject({ message: '登录失败，请重试' })
        }

        // 保存Token信息
        TokenManager.saveTokens(token, res.data.refreshToken)
        
        this.globalData.userInfo = user
        
        // 更新状态管理器
        globalStateManager.setState('isLoggedIn', true)
        globalStateManager.setState('userInfo', user)
        
        wx.setStorageSync('userInfo', user)
        
        Logger.info('Login successful', { username: cleanUsername })
      }
      return res
    }).catch(error => {
      Logger.error('Login failed', { username: cleanUsername, error })
      throw error
    })
  },

  // 登出方法 - 使用TokenManager清理
  logout() {
    Logger.info('User logout')
    
    TokenManager.clearTokens()
    this.globalData.userInfo = null
    
    globalStateManager.setState('isLoggedIn', false)
    globalStateManager.setState('userInfo', null)
    
    // 清理相关缓存
    CacheManager.remove('user_preferences')
    CacheManager.remove('recent_searches')
    
    wx.removeStorageSync('userInfo')
    
    wx.redirectTo({ url: '/pages/login/login' })
  },

  // 检查登录状态 - 增强验证
  checkLogin() {
    const isLoggedIn = this.globalData.isLoggedIn
    const token = this.globalData.token || wx.getStorageSync('token')
    
    if (!isLoggedIn || !token || SecurityUtils.isTokenExpired(token)) {
      Logger.warn('Login check failed, redirecting to login')
      this.logout()
      return false
    }
    
    return true
  },

  // 获取状态管理器实例
  getStateManager() {
    return globalStateManager
  }
})

