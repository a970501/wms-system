// utils/test-utils.js - 测试工具类
const Logger = require('./logger')

class TestUtils {
  /**
   * 模拟数据生成器
   */
  static mockData = {
    // 生成模拟计件记录
    generatePieceworkRecords(count = 10) {
      const products = ['闸阀盖', '闸阀体', '截止阀盖', '过滤器体']
      const specs = ['DN15', 'DN20', 'DN25', 'DN32']
      const materials = ['304', '316', '304重']
      
      return Array.from({ length: count }, (_, i) => ({
        id: i + 1,
        productName: products[Math.floor(Math.random() * products.length)],
        specification: specs[Math.floor(Math.random() * specs.length)],
        material: materials[Math.floor(Math.random() * materials.length)],
        quantity: Math.floor(Math.random() * 100) + 1,
        unitPrice: (Math.random() * 10 + 1).toFixed(2),
        totalAmount: ((Math.random() * 10 + 1) * (Math.floor(Math.random() * 100) + 1)).toFixed(2),
        workDate: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
        workerName: '测试用户',
        semiFinished: Math.random() > 0.8 ? '是' : '',
        defectQuantity: Math.random() > 0.9 ? Math.floor(Math.random() * 5) : 0
      }))
    },

    // 生成模拟库存数据
    generateInventoryItems(count = 20) {
      const products = ['闸阀盖', '闸阀体', '截止阀盖', '过滤器体']
      const specs = ['DN15', 'DN20', 'DN25', 'DN32']
      const materials = ['304', '316', '304重']
      
      return Array.from({ length: count }, (_, i) => ({
        id: i + 1,
        productName: products[Math.floor(Math.random() * products.length)],
        specification: specs[Math.floor(Math.random() * specs.length)],
        material: materials[Math.floor(Math.random() * materials.length)],
        quantity: Math.floor(Math.random() * 1000) + 10,
        unit: '个',
        connectionType: Math.random() > 0.5 ? 'BSPT' : 'NPT'
      }))
    }
  }

  /**
   * 性能测试工具
   */
  static performance = {
    // 测试函数执行时间
    async measureTime(fn, label = 'Function') {
      const start = Date.now()
      const result = await fn()
      const end = Date.now()
      Logger.info(`${label} execution time: ${end - start}ms`)
      return result
    },

    // 内存使用监控
    checkMemoryUsage() {
      try {
        const info = wx.getStorageInfoSync()
        Logger.info('Storage usage', {
          currentSize: info.currentSize,
          limitSize: info.limitSize,
          usage: `${((info.currentSize / info.limitSize) * 100).toFixed(2)}%`
        })
        return info
      } catch (error) {
        Logger.error('Failed to get storage info', error)
        return null
      }
    }
  }

  /**
   * API测试工具
   */
  static api = {
    // 测试API连通性
    async testConnectivity() {
      const app = getApp()
      const testEndpoints = [
        '/piecework?page=0&size=1',
        '/inventory/items?page=0&size=1',
        '/users'
      ]

      const results = []
      for (const endpoint of testEndpoints) {
        try {
          const start = Date.now()
          await app.request({ url: endpoint })
          const responseTime = Date.now() - start
          results.push({ endpoint, status: 'success', responseTime })
        } catch (error) {
          results.push({ endpoint, status: 'failed', error: error.message })
        }
      }

      Logger.info('API connectivity test results', results)
      return results
    },

    // 压力测试
    async stressTest(endpoint, concurrency = 5, iterations = 10) {
      const app = getApp()
      const results = []
      
      for (let i = 0; i < iterations; i++) {
        const promises = Array.from({ length: concurrency }, async () => {
          const start = Date.now()
          try {
            await app.request({ url: endpoint })
            return { success: true, responseTime: Date.now() - start }
          } catch (error) {
            return { success: false, error: error.message }
          }
        })
        
        const batchResults = await Promise.all(promises)
        results.push(...batchResults)
      }

      const successCount = results.filter(r => r.success).length
      const avgResponseTime = results
        .filter(r => r.success)
        .reduce((sum, r) => sum + r.responseTime, 0) / successCount

      Logger.info('Stress test results', {
        endpoint,
        totalRequests: results.length,
        successCount,
        successRate: `${((successCount / results.length) * 100).toFixed(2)}%`,
        avgResponseTime: `${avgResponseTime.toFixed(2)}ms`
      })

      return results
    }
  }

  /**
   * 用户行为模拟
   */
  static simulation = {
    // 模拟用户操作序列
    async simulateUserFlow(actions) {
      Logger.info('Starting user flow simulation', { actionsCount: actions.length })
      
      for (let i = 0; i < actions.length; i++) {
        const action = actions[i]
        try {
          Logger.debug(`Executing action ${i + 1}/${actions.length}`, action)
          
          if (action.type === 'tap') {
            // 模拟点击
            await this.delay(action.delay || 100)
          } else if (action.type === 'input') {
            // 模拟输入
            await this.delay(action.delay || 200)
          } else if (action.type === 'api') {
            // 模拟API调用
            const app = getApp()
            await app.request(action.request)
          }
          
          Logger.debug(`Action ${i + 1} completed`)
        } catch (error) {
          Logger.error(`Action ${i + 1} failed`, error)
          throw error
        }
      }
      
      Logger.info('User flow simulation completed')
    },

    delay(ms) {
      return new Promise(resolve => setTimeout(resolve, ms))
    }
  }

  /**
   * 错误注入测试
   */
  static errorInjection = {
    // 模拟网络错误
    simulateNetworkError() {
      const originalRequest = wx.request
      wx.request = function(options) {
        if (Math.random() < 0.3) { // 30% 概率失败
          setTimeout(() => {
            options.fail && options.fail({ errMsg: 'request:fail timeout' })
          }, 100)
        } else {
          originalRequest.call(this, options)
        }
      }
      
      // 5秒后恢复
      setTimeout(() => {
        wx.request = originalRequest
        Logger.info('Network error simulation ended')
      }, 5000)
      
      Logger.warn('Network error simulation started (30% failure rate)')
    },

    // 模拟存储错误
    simulateStorageError() {
      const originalSetStorage = wx.setStorageSync
      wx.setStorageSync = function(key, data) {
        if (Math.random() < 0.2) { // 20% 概率失败
          throw new Error('Storage quota exceeded')
        }
        return originalSetStorage.call(this, key, data)
      }
      
      setTimeout(() => {
        wx.setStorageSync = originalSetStorage
        Logger.info('Storage error simulation ended')
      }, 3000)
      
      Logger.warn('Storage error simulation started (20% failure rate)')
    }
  }
}

module.exports = TestUtils