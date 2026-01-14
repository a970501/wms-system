// utils/offline-manager.js - 离线数据管理器
const Logger = require('./logger')
const CacheManager = require('./cache-manager')

class OfflineManager {
  static OFFLINE_QUEUE_KEY = 'offline_queue'
  static OFFLINE_DATA_KEY = 'offline_data'
  static MAX_QUEUE_SIZE = 100

  /**
   * 检查网络状态
   * @returns {Promise<boolean>} 是否在线
   */
  static async checkNetworkStatus() {
    return new Promise((resolve) => {
      wx.getNetworkType({
        success: (res) => {
          const isOnline = res.networkType !== 'none'
          Logger.debug('Network status checked', { 
            networkType: res.networkType, 
            isOnline 
          })
          resolve(isOnline)
        },
        fail: () => {
          Logger.warn('Failed to get network type, assuming offline')
          resolve(false)
        }
      })
    })
  }

  /**
   * 监听网络状态变化
   */
  static startNetworkMonitoring() {
    wx.onNetworkStatusChange((res) => {
      Logger.info('Network status changed', res)
      
      if (res.isConnected) {
        Logger.info('Network connected, syncing offline data')
        OfflineManager.syncOfflineData()
      } else {
        Logger.warn('Network disconnected, entering offline mode')
      }
    })
  }

  /**
   * 保存离线操作到队列
   * @param {Object} operation 操作对象
   */
  static saveOfflineOperation(operation) {
    try {
      const queue = wx.getStorageSync(OfflineManager.OFFLINE_QUEUE_KEY) || []
      
      // 添加时间戳和唯一ID
      const operationWithMeta = {
        ...operation,
        id: OfflineManager.generateId(),
        timestamp: Date.now(),
        retryCount: 0
      }
      
      queue.push(operationWithMeta)
      
      // 限制队列大小
      if (queue.length > OfflineManager.MAX_QUEUE_SIZE) {
        queue.shift() // 移除最旧的操作
        Logger.warn('Offline queue size exceeded, removing oldest operation')
      }
      
      wx.setStorageSync(OfflineManager.OFFLINE_QUEUE_KEY, queue)
      Logger.info('Offline operation saved', { 
        operationType: operation.type,
        queueSize: queue.length 
      })
      
    } catch (error) {
      Logger.error('Failed to save offline operation', error)
    }
  }

  /**
   * 保存离线数据
   * @param {string} key 数据键
   * @param {any} data 数据
   */
  static saveOfflineData(key, data) {
    try {
      const offlineData = wx.getStorageSync(OfflineManager.OFFLINE_DATA_KEY) || {}
      offlineData[key] = {
        data,
        timestamp: Date.now()
      }
      
      wx.setStorageSync(OfflineManager.OFFLINE_DATA_KEY, offlineData)
      Logger.debug('Offline data saved', { key, dataSize: JSON.stringify(data).length })
      
    } catch (error) {
      Logger.error('Failed to save offline data', error)
    }
  }

  /**
   * 获取离线数据
   * @param {string} key 数据键
   * @param {number} maxAge 最大缓存时间(毫秒)
   * @returns {any|null} 离线数据或null
   */
  static getOfflineData(key, maxAge = 24 * 60 * 60 * 1000) {
    try {
      const offlineData = wx.getStorageSync(OfflineManager.OFFLINE_DATA_KEY) || {}
      const item = offlineData[key]
      
      if (!item) return null
      
      const age = Date.now() - item.timestamp
      if (age > maxAge) {
        Logger.debug('Offline data expired', { key, age })
        delete offlineData[key]
        wx.setStorageSync(OfflineManager.OFFLINE_DATA_KEY, offlineData)
        return null
      }
      
      Logger.debug('Offline data retrieved', { key, age })
      return item.data
      
    } catch (error) {
      Logger.error('Failed to get offline data', error)
      return null
    }
  }

  /**
   * 同步离线数据
   */
  static async syncOfflineData() {
    const isOnline = await OfflineManager.checkNetworkStatus()
    if (!isOnline) {
      Logger.warn('Cannot sync offline data: no network connection')
      return
    }

    const queue = wx.getStorageSync(OfflineManager.OFFLINE_QUEUE_KEY) || []
    if (queue.length === 0) {
      Logger.debug('No offline operations to sync')
      return
    }

    Logger.info(`Starting offline data sync: ${queue.length} operations`)
    
    const app = getApp()
    const successfulOperations = []
    const failedOperations = []

    for (const operation of queue) {
      try {
        await OfflineManager.executeOperation(app, operation)
        successfulOperations.push(operation)
        Logger.debug('Offline operation synced successfully', { 
          id: operation.id, 
          type: operation.type 
        })
        
      } catch (error) {
        operation.retryCount = (operation.retryCount || 0) + 1
        
        if (operation.retryCount >= 3) {
          Logger.error('Offline operation failed after max retries', { 
            id: operation.id, 
            type: operation.type, 
            error 
          })
          // 可以选择丢弃或移到失败队列
        } else {
          failedOperations.push(operation)
          Logger.warn('Offline operation failed, will retry', { 
            id: operation.id, 
            type: operation.type, 
            retryCount: operation.retryCount 
          })
        }
      }
    }

    // 更新队列，只保留失败的操作
    wx.setStorageSync(OfflineManager.OFFLINE_QUEUE_KEY, failedOperations)
    
    Logger.info('Offline data sync completed', {
      successful: successfulOperations.length,
      failed: failedOperations.length
    })

    // 通知同步完成
    if (successfulOperations.length > 0) {
      wx.showToast({
        title: `已同步${successfulOperations.length}条离线数据`,
        icon: 'success'
      })
    }
  }

  /**
   * 执行离线操作
   * @param {Object} app 应用实例
   * @param {Object} operation 操作对象
   */
  static async executeOperation(app, operation) {
    const { type, url, method, data } = operation
    
    switch (type) {
      case 'api_request':
        return await app.request({ url, method, data })
      
      case 'piecework_add':
        return await app.request({
          url: '/piecework',
          method: 'POST',
          data: operation.data
        })
      
      case 'inventory_update':
        return await app.request({
          url: `/inventory/items/${operation.itemId}`,
          method: 'PUT',
          data: operation.data
        })
      
      default:
        throw new Error(`Unknown operation type: ${type}`)
    }
  }

  /**
   * 生成唯一ID
   * @returns {string} 唯一ID
   */
  static generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2)
  }

  /**
   * 清空离线队列
   */
  static clearOfflineQueue() {
    try {
      wx.removeStorageSync(OfflineManager.OFFLINE_QUEUE_KEY)
      Logger.info('Offline queue cleared')
    } catch (error) {
      Logger.error('Failed to clear offline queue', error)
    }
  }

  /**
   * 清空离线数据
   */
  static clearOfflineData() {
    try {
      wx.removeStorageSync(OfflineManager.OFFLINE_DATA_KEY)
      Logger.info('Offline data cleared')
    } catch (error) {
      Logger.error('Failed to clear offline data', error)
    }
  }

  /**
   * 获取离线队列状态
   * @returns {Object} 队列状态信息
   */
  static getOfflineQueueStatus() {
    try {
      const queue = wx.getStorageSync(OfflineManager.OFFLINE_QUEUE_KEY) || []
      const offlineData = wx.getStorageSync(OfflineManager.OFFLINE_DATA_KEY) || {}
      
      return {
        queueSize: queue.length,
        dataKeys: Object.keys(offlineData).length,
        oldestOperation: queue.length > 0 ? queue[0].timestamp : null,
        newestOperation: queue.length > 0 ? queue[queue.length - 1].timestamp : null
      }
    } catch (error) {
      Logger.error('Failed to get offline queue status', error)
      return {
        queueSize: 0,
        dataKeys: 0,
        oldestOperation: null,
        newestOperation: null
      }
    }
  }

  /**
   * 创建离线友好的API请求
   * @param {Object} app 应用实例
   * @param {Object} options 请求选项
   * @param {boolean} enableOffline 是否启用离线模式
   * @returns {Promise} 请求结果
   */
  static async createOfflineRequest(app, options, enableOffline = true) {
    const isOnline = await OfflineManager.checkNetworkStatus()
    
    if (isOnline) {
      try {
        const result = await app.request(options)
        
        // 缓存GET请求的结果
        if (options.method === 'GET' || !options.method) {
          const cacheKey = `api_${options.url}`
          OfflineManager.saveOfflineData(cacheKey, result)
        }
        
        return result
      } catch (error) {
        // 网络请求失败，尝试使用离线数据
        if (enableOffline && (options.method === 'GET' || !options.method)) {
          const cacheKey = `api_${options.url}`
          const offlineData = OfflineManager.getOfflineData(cacheKey)
          
          if (offlineData) {
            Logger.info('Using offline data due to network error', { url: options.url })
            return offlineData
          }
        }
        
        throw error
      }
    } else {
      // 离线模式
      if (options.method === 'GET' || !options.method) {
        // GET请求尝试使用离线数据
        const cacheKey = `api_${options.url}`
        const offlineData = OfflineManager.getOfflineData(cacheKey)
        
        if (offlineData) {
          Logger.info('Using offline data in offline mode', { url: options.url })
          return offlineData
        } else {
          throw new Error('离线模式下无可用数据')
        }
      } else {
        // 非GET请求保存到离线队列
        if (enableOffline) {
          OfflineManager.saveOfflineOperation({
            type: 'api_request',
            url: options.url,
            method: options.method,
            data: options.data
          })
          
          Logger.info('Operation saved for offline sync', { 
            url: options.url, 
            method: options.method 
          })
          
          return { 
            code: 200, 
            message: '操作已保存，将在网络恢复后同步',
            offline: true 
          }
        } else {
          throw new Error('网络连接不可用')
        }
      }
    }
  }
}

module.exports = OfflineManager