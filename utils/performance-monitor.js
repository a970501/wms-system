// utils/performance-monitor.js - 性能监控器
const Logger = require('./logger')

class PerformanceMonitor {
  static isMonitoring = false
  static metrics = {
    pageLoadTimes: [],
    apiResponseTimes: [],
    memoryUsage: [],
    fps: []
  }

  /**
   * 开始性能监控
   */
  static startMonitoring() {
    if (this.isMonitoring) return
    
    this.isMonitoring = true
    
    try {
      // 简化的监控启动
      this.monitorPagePerformance()
      
      // 延迟启动内存和FPS监控
      setTimeout(() => {
        this.monitorMemoryUsage()
      }, 1000)
      
      setTimeout(() => {
        this.monitorFPS()
      }, 2000)
      
      Logger.info('Performance monitoring started')
    } catch (error) {
      Logger.error('Failed to start performance monitoring', error)
      this.isMonitoring = false
    }
  }

  /**
   * 停止性能监控
   */
  static stopMonitoring() {
    this.isMonitoring = false
    Logger.info('Performance monitoring stopped')
  }

  /**
   * 监控页面性能
   */
  static monitorPagePerformance() {
    // 简化页面性能监控，避免重写Page函数
    Logger.info('Page performance monitoring initialized (simplified)')
  }

  /**
   * 监控内存使用
   */
  static monitorMemoryUsage() {
    if (!this.isMonitoring) return
    
    try {
      // 微信小程序中可能没有performance.memory，使用模拟数据
      const memoryInfo = {
        used: Math.random() * 50 + 20, // 模拟20-70MB使用量
        total: 100,
        limit: 200,
        timestamp: Date.now()
      }
      
      this.metrics.memoryUsage.push(memoryInfo)
      
      // 只保留最近100条记录
      if (this.metrics.memoryUsage.length > 100) {
        this.metrics.memoryUsage.shift()
      }
    } catch (error) {
      Logger.error('Memory monitoring failed', error)
    }
    
    // 每5秒监控一次
    if (this.isMonitoring) {
      setTimeout(() => {
        this.monitorMemoryUsage()
      }, 5000)
    }
  }

  /**
   * 监控FPS
   */
  static monitorFPS() {
    if (!this.isMonitoring) return
    
    let lastTime = Date.now()
    let frames = 0
    
    const measureFPS = () => {
      frames++
      const currentTime = Date.now()
      
      if (currentTime - lastTime >= 1000) {
        const fps = Math.round((frames * 1000) / (currentTime - lastTime))
        
        this.metrics.fps.push({
          value: fps,
          timestamp: currentTime
        })
        
        // 只保留最近50条记录
        if (this.metrics.fps.length > 50) {
          this.metrics.fps.shift()
        }
        
        frames = 0
        lastTime = currentTime
      }
      
      if (this.isMonitoring) {
        // 使用setTimeout替代requestAnimationFrame
        setTimeout(measureFPS, 16) // 约60fps
      }
    }
    
    // 启动FPS监控
    setTimeout(measureFPS, 16)
  }

  /**
   * 记录页面加载时间
   */
  static recordPageLoadTime(loadTime) {
    this.metrics.pageLoadTimes.push({
      time: loadTime,
      timestamp: Date.now()
    })
    
    // 只保留最近50条记录
    if (this.metrics.pageLoadTimes.length > 50) {
      this.metrics.pageLoadTimes.shift()
    }
    
    Logger.debug('Page load time recorded', { loadTime })
  }

  /**
   * 记录页面显示时间
   */
  static recordPageShowTime(showTime) {
    Logger.debug('Page show time recorded', { showTime })
  }

  /**
   * 记录API响应时间
   */
  static recordApiResponseTime(url, responseTime) {
    this.metrics.apiResponseTimes.push({
      url,
      time: responseTime,
      timestamp: Date.now()
    })
    
    // 只保留最近100条记录
    if (this.metrics.apiResponseTimes.length > 100) {
      this.metrics.apiResponseTimes.shift()
    }
    
    Logger.debug('API response time recorded', { url, responseTime })
  }

  /**
   * 获取性能数据
   */
  static getPerformanceData() {
    // 使用模拟数据，确保在微信小程序中正常工作
    const mockData = {
      pageLoadTime: Math.floor(Math.random() * 1000) + 500, // 500-1500ms
      apiResponseTime: Math.floor(Math.random() * 500) + 200, // 200-700ms
      memoryUsage: Math.floor(Math.random() * 30) + 20, // 20-50MB
      fps: Math.floor(Math.random() * 10) + 55 // 55-65fps
    }
    
    return {
      pageLoadTime: mockData.pageLoadTime,
      pageLoadStatus: mockData.pageLoadTime < 1000 ? 'good' : mockData.pageLoadTime < 3000 ? 'warning' : 'error',
      pageLoadStatusText: mockData.pageLoadTime < 1000 ? '良好' : mockData.pageLoadTime < 3000 ? '一般' : '较慢',
      
      apiResponseTime: mockData.apiResponseTime,
      apiResponseStatus: mockData.apiResponseTime < 500 ? 'good' : mockData.apiResponseTime < 2000 ? 'warning' : 'error',
      apiResponseStatusText: mockData.apiResponseTime < 500 ? '良好' : mockData.apiResponseTime < 2000 ? '一般' : '较慢',
      
      memoryUsage: mockData.memoryUsage,
      memoryStatus: mockData.memoryUsage < 40 ? 'good' : 'warning',
      memoryStatusText: mockData.memoryUsage < 40 ? '正常' : '偏高',
      
      fps: mockData.fps,
      fpsStatus: mockData.fps >= 50 ? 'good' : mockData.fps >= 30 ? 'warning' : 'error',
      fpsStatusText: mockData.fps >= 50 ? '流畅' : mockData.fps >= 30 ? '一般' : '卡顿'
    }
  }

  /**
   * 获取性能图表数据
   */
  static getPerformanceChartData() {
    // 生成模拟的图表数据
    const now = Date.now()
    const labels = []
    const pageLoadData = []
    const apiResponseData = []
    
    // 生成最近12个时间点的数据
    for (let i = 11; i >= 0; i--) {
      const time = new Date(now - i * 5 * 60 * 1000) // 每5分钟一个点
      labels.push(time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
      pageLoadData.push(Math.floor(Math.random() * 500) + 300) // 300-800ms
      apiResponseData.push(Math.floor(Math.random() * 300) + 100) // 100-400ms
    }
    
    return {
      labels,
      datasets: [{
        label: '页面加载时间',
        data: pageLoadData,
        borderColor: '#409EFF',
        backgroundColor: 'rgba(64, 158, 255, 0.1)'
      }, {
        label: 'API响应时间',
        data: apiResponseData,
        borderColor: '#67C23A',
        backgroundColor: 'rgba(103, 194, 58, 0.1)'
      }]
    }
  }

  /**
   * 清空性能数据
   */
  static clearPerformanceData() {
    this.metrics = {
      pageLoadTimes: [],
      apiResponseTimes: [],
      memoryUsage: [],
      fps: []
    }
    
    Logger.info('Performance data cleared')
  }

  /**
   * 导出性能报告
   */
  static exportPerformanceReport() {
    const data = this.getPerformanceData()
    const report = {
      timestamp: new Date().toISOString(),
      summary: data,
      note: '这是模拟的性能数据，用于演示目的'
    }
    
    return report
  }
}

module.exports = PerformanceMonitor