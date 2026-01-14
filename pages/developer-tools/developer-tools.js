// pages/developer-tools/developer-tools.js - 开发者工具
var app = getApp()
var Logger = require('../../utils/logger')
var LoadingManager = require('../../utils/loading-manager')
var PerformanceMonitor = require('../../utils/performance-monitor')

Page({
  data: {
    currentTab: 'performance', // performance, debug, system, logs
    
    // 性能数据
    performanceData: {
      pageLoadTime: 0,
      pageLoadStatus: 'good',
      pageLoadStatusText: '良好',
      apiResponseTime: 0,
      apiResponseStatus: 'good', 
      apiResponseStatusText: '良好',
      memoryUsage: 0,
      memoryStatus: 'good',
      memoryStatusText: '正常',
      fps: 60,
      fpsStatus: 'good',
      fpsStatusText: '流畅'
    },
    
    // 性能图表数据
    performanceChartData: {
      labels: [],
      datasets: []
    },
    
    // 性能建议
    performanceRecommendations: [],
    
    // 监控状态
    isMonitoring: false,
    
    // 控制台日志
    consoleLogs: [],
    autoScroll: true,
    consoleScrollTop: 0,
    
    // 网络请求
    networkRequests: [],
    
    // 系统信息
    systemInfo: {},
    appInfo: {
      version: '1.0.0',
      buildTime: '2024-01-01',
      uptime: '0分钟',
      pageCount: 20,
      cacheSize: '2.5MB',
      storageUsage: '1.2MB'
    },
    
    // 用户信息
    currentUserInfo: {},
    
    // 环境变量
    envVariables: [],
    
    // 日志过滤
    logFilter: {
      level: 'all',
      time: 'all'
    },
    
    // 过滤后的日志
    filteredLogs: [],
    
    loading: false
  },

  onLoad: function() {
    Logger.info('Developer tools page loaded')
    
    // 检查管理员权限
    if (!this.checkAdminPermission()) {
      return
    }
    
    this.initSystemInfo()
    this.initAppInfo()
    this.initUserInfo()
    this.initConsoleLogs()
  },

  onShow: function() {
    if (!app.checkLogin()) return
    
    // 再次检查管理员权限
    if (!this.checkAdminPermission()) {
      return
    }
    
    this.loadPerformanceData()
  },

  /**
   * 检查管理员权限
   */
  checkAdminPermission: function() {
    var userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    
    if (userInfo.role !== 'ADMIN') {
      wx.showModal({
        title: '访问受限',
        content: '开发者工具仅限管理员使用',
        showCancel: false,
        success: function() {
          wx.navigateBack({
            fail: function() {
              wx.switchTab({ url: '/pages/index/index' })
            }
          })
        }
      })
      return false
    }
    
    return true
  },

  /**
   * 初始化系统信息
   */
  initSystemInfo: function() {
    try {
      var systemInfo = wx.getSystemInfoSync()
      this.setData({ systemInfo: systemInfo })
      
      // 设置环境变量
      var envVariables = [
        { key: 'platform', value: systemInfo.platform },
        { key: 'version', value: systemInfo.version },
        { key: 'SDKVersion', value: systemInfo.SDKVersion },
        { key: 'brand', value: systemInfo.brand },
        { key: 'model', value: systemInfo.model }
      ]
      
      this.setData({ envVariables: envVariables })
    } catch (error) {
      Logger.error('Failed to get system info', error)
    }
  },

  /**
   * 初始化用户信息
   */
  initUserInfo: function() {
    var userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    
    // 生成模拟的用户ID信息（实际项目中应该从后端获取）
    var currentUserInfo = {
      username: userInfo.username || '未知用户',
      role: userInfo.role || 'USER',
      userId: userInfo.userId || 'user_' + Date.now(),
      openId: userInfo.openId || 'openid_' + Math.random().toString(36).substr(2, 9),
      unionId: userInfo.unionId || 'unionid_' + Math.random().toString(36).substr(2, 9),
      sessionKey: '***已隐藏***',
      loginTime: userInfo.loginTime || new Date().toLocaleString('zh-CN'),
      lastActiveTime: new Date().toLocaleString('zh-CN')
    }
    
    this.setData({ currentUserInfo: currentUserInfo })
  },

  /**
   * 初始化应用信息
   */
  initAppInfo: function() {
    var self = this
    var startTime = Date.now()
    
    // 计算运行时长
    var updateUptime = function() {
      var uptime = Date.now() - startTime
      var minutes = Math.floor(uptime / 60000)
      var seconds = Math.floor((uptime % 60000) / 1000)
      
      self.setData({
        'appInfo.uptime': minutes + '分' + seconds + '秒'
      })
    }
    
    updateUptime()
    setInterval(updateUptime, 1000)
  },

  /**
   * 初始化控制台日志
   */
  initConsoleLogs: function() {
    // 模拟一些日志
    var mockLogs = [
      { id: 1, level: 'info', message: 'Application started', timestamp: Date.now() - 10000 },
      { id: 2, level: 'debug', message: 'Loading user preferences', timestamp: Date.now() - 8000 },
      { id: 3, level: 'warn', message: 'Cache miss for key: user_data', timestamp: Date.now() - 5000 },
      { id: 4, level: 'info', message: 'API request completed successfully', timestamp: Date.now() - 2000 }
    ]
    
    this.setData({ 
      consoleLogs: mockLogs,
      filteredLogs: mockLogs
    })
  },

  /**
   * 切换标签页
   */
  switchTab: function(e) {
    var tab = e.currentTarget.dataset.tab
    this.setData({ currentTab: tab })
    
    switch (tab) {
      case 'performance':
        this.loadPerformanceData()
        break
      case 'debug':
        this.loadDebugData()
        break
      case 'system':
        this.loadSystemData()
        break
      case 'logs':
        this.loadLogs()
        break
    }
  },

  /**
   * 加载性能数据
   */
  loadPerformanceData: function() {
    try {
      var performanceData = PerformanceMonitor.getPerformanceData()
      var chartData = PerformanceMonitor.getPerformanceChartData()
      
      this.setData({
        performanceData: performanceData,
        performanceChartData: chartData
      })
      
      // 生成性能建议
      this.generatePerformanceRecommendations(performanceData)
    } catch (error) {
      Logger.error('Failed to load performance data', error)
    }
  },

  /**
   * 生成性能建议
   */
  generatePerformanceRecommendations: function(data) {
    var recommendations = []
    
    if (data.pageLoadTime > 3000) {
      recommendations.push({
        id: 1,
        type: '页面加载优化',
        priority: 'high',
        priorityText: '高',
        message: '页面加载时间过长，影响用户体验',
        action: '优化图片大小，减少网络请求，使用缓存'
      })
    }
    
    if (data.apiResponseTime > 2000) {
      recommendations.push({
        id: 2,
        type: 'API性能优化',
        priority: 'medium',
        priorityText: '中',
        message: 'API响应时间较慢',
        action: '检查网络连接，优化后端接口，添加请求缓存'
      })
    }
    
    if (data.memoryUsage > 100) {
      recommendations.push({
        id: 3,
        type: '内存优化',
        priority: 'medium',
        priorityText: '中',
        message: '内存使用量较高',
        action: '清理无用变量，优化数据结构，及时释放资源'
      })
    }
    
    this.setData({ performanceRecommendations: recommendations })
  },

  /**
   * 开始/停止性能监控
   */
  startPerformanceMonitoring: function() {
    var isMonitoring = !this.data.isMonitoring
    
    if (isMonitoring) {
      PerformanceMonitor.startMonitoring()
      LoadingManager.showSuccess('性能监控已开启')
    } else {
      PerformanceMonitor.stopMonitoring()
      LoadingManager.showSuccess('性能监控已停止')
    }
    
    this.setData({ isMonitoring: isMonitoring })
  },

  /**
   * 清空性能数据
   */
  clearPerformanceData: function() {
    var self = this
    wx.showModal({
      title: '确认清空',
      content: '确定要清空所有性能数据吗？',
      success: function(res) {
        if (res.confirm) {
          PerformanceMonitor.clearPerformanceData()
          self.loadPerformanceData()
          LoadingManager.showSuccess('性能数据已清空')
        }
      }
    })
  },

  /**
   * 导出性能报告
   */
  exportPerformanceReport: function() {
    try {
      var report = PerformanceMonitor.exportPerformanceReport()
      var reportText = JSON.stringify(report, null, 2)
      
      wx.setClipboardData({
        data: reportText,
        success: function() {
          LoadingManager.showSuccess('性能报告已复制到剪贴板')
        }
      })
    } catch (error) {
      Logger.error('Failed to export performance report', error)
      LoadingManager.showError('导出失败')
    }
  },

  /**
   * 加载调试数据
   */
  loadDebugData: function() {
    // 模拟网络请求数据
    var mockRequests = [
      {
        id: 1,
        method: 'GET',
        url: '/api/piecework',
        status: 200,
        statusClass: 'success',
        duration: 245,
        size: 12.5,
        timestamp: Date.now() - 30000
      },
      {
        id: 2,
        method: 'POST',
        url: '/api/inventory',
        status: 201,
        statusClass: 'success',
        duration: 180,
        size: 8.2,
        timestamp: Date.now() - 15000
      }
    ]
    
    this.setData({ networkRequests: mockRequests })
  },

  /**
   * 清空控制台
   */
  clearConsole: function() {
    this.setData({ 
      consoleLogs: [],
      filteredLogs: []
    })
    LoadingManager.showSuccess('控制台已清空')
  },

  /**
   * 切换自动滚动
   */
  toggleAutoScroll: function() {
    var autoScroll = !this.data.autoScroll
    this.setData({ autoScroll: autoScroll })
    LoadingManager.showToast(autoScroll ? '自动滚动已开启' : '自动滚动已关闭', 'none')
  },

  /**
   * 调试工具操作
   */
  inspectElement: function() {
    LoadingManager.showToast('元素检查功能开发中', 'none')
  },

  measurePerformance: function() {
    var self = this
    LoadingManager.show('测量中...')
    setTimeout(function() {
      self.loadPerformanceData()
      LoadingManager.showSuccess('性能测量完成')
    }, 1000)
  },

  testAPI: function() {
    LoadingManager.show('测试中...')
    app.request({
      url: '/piecework?page=0&size=1'
    }).then(function() {
      LoadingManager.showSuccess('API测试成功')
    }).catch(function() {
      LoadingManager.showError('API测试失败')
    })
  },

  simulateError: function() {
    Logger.error('Simulated error for testing', { test: true })
    LoadingManager.showToast('已模拟错误，请查看日志', 'none')
  },

  /**
   * 加载系统数据
   */
  loadSystemData: function() {
    // 系统数据已在onLoad中初始化
  },

  /**
   * 设置日志过滤器
   */
  setLogFilter: function(e) {
    var type = e.currentTarget.dataset.type
    var value = e.currentTarget.dataset.value
    
    var updateData = {}
    updateData['logFilter.' + type] = value
    this.setData(updateData)
    
    this.filterLogs()
  },

  /**
   * 过滤日志
   */
  filterLogs: function() {
    var filteredLogs = this.data.consoleLogs.slice()
    
    // 按级别过滤
    if (this.data.logFilter.level !== 'all') {
      filteredLogs = filteredLogs.filter(function(log) {
        return log.level === this.data.logFilter.level
      }.bind(this))
    }
    
    // 按时间过滤
    if (this.data.logFilter.time !== 'all') {
      var now = Date.now()
      var timeLimit = 0
      
      switch (this.data.logFilter.time) {
        case 'hour':
          timeLimit = now - 60 * 60 * 1000
          break
        case 'day':
          timeLimit = now - 24 * 60 * 60 * 1000
          break
      }
      
      if (timeLimit > 0) {
        filteredLogs = filteredLogs.filter(function(log) {
          return log.timestamp > timeLimit
        })
      }
    }
    
    this.setData({ filteredLogs: filteredLogs })
  },

  /**
   * 加载日志
   */
  loadLogs: function() {
    this.filterLogs()
  },

  /**
   * 刷新日志
   */
  refreshLogs: function() {
    var self = this
    LoadingManager.show('刷新中...')
    setTimeout(function() {
      self.initConsoleLogs()
      LoadingManager.showSuccess('日志已刷新')
    }, 500)
  },

  /**
   * 清空日志
   */
  clearLogs: function() {
    var self = this
    wx.showModal({
      title: '确认清空',
      content: '确定要清空所有日志吗？',
      success: function(res) {
        if (res.confirm) {
          self.setData({ 
            consoleLogs: [],
            filteredLogs: []
          })
          LoadingManager.showSuccess('日志已清空')
        }
      }
    })
  },

  /**
   * 导出日志
   */
  exportLogs: function() {
    var self = this
    try {
      var logsText = this.data.filteredLogs.map(function(log) {
        return '[' + self.formatFullTime(log.timestamp) + '] [' + log.level.toUpperCase() + '] ' + log.message
      }).join('\n')
      
      wx.setClipboardData({
        data: logsText,
        success: function() {
          LoadingManager.showSuccess('日志已复制到剪贴板')
        }
      })
    } catch (error) {
      Logger.error('Failed to export logs', error)
      LoadingManager.showError('导出失败')
    }
  },

  /**
   * 复制用户ID信息
   */
  copyUserInfo: function(e) {
    var type = e.currentTarget.dataset.type
    var value = e.currentTarget.dataset.value
    
    wx.setClipboardData({
      data: value,
      success: function() {
        var typeNames = {
          'userId': '用户ID',
          'openId': 'OpenID',
          'unionId': 'UnionID'
        }
        LoadingManager.showSuccess((typeNames[type] || '信息') + '已复制到剪贴板')
      }
    })
  },

  /**
   * 格式化时间
   */
  formatTime: function(timestamp) {
    var date = new Date(timestamp)
    return date.toLocaleTimeString('zh-CN')
  },

  /**
   * 格式化完整时间
   */
  formatFullTime: function(timestamp) {
    var date = new Date(timestamp)
    return date.toLocaleString('zh-CN')
  }
})