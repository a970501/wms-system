// utils/cache-manager.js - 缓存管理器
const Logger = require('./logger')

class CacheManager {
  static DEFAULT_EXPIRE_TIME = 5 * 60 * 1000 // 5分钟

  /**
   * 设置缓存
   * @param {string} key 缓存键
   * @param {any} data 缓存数据
   * @param {number} expireTime 过期时间(毫秒)
   */
  static set(key, data, expireTime = CacheManager.DEFAULT_EXPIRE_TIME) {
    try {
      const cacheData = {
        data,
        timestamp: Date.now(),
        expireTime
      }
      wx.setStorageSync(key, cacheData)
      Logger.debug(`Cache set: ${key}`, { expireTime })
    } catch (error) {
      Logger.error(`Cache set failed: ${key}`, error)
    }
  }

  /**
   * 获取缓存
   * @param {string} key 缓存键
   * @returns {any|null} 缓存数据或null
   */
  static get(key) {
    try {
      const cache = wx.getStorageSync(key)
      if (!cache) return null

      const now = Date.now()
      if (now - cache.timestamp < cache.expireTime) {
        Logger.debug(`Cache hit: ${key}`)
        return cache.data
      } else {
        Logger.debug(`Cache expired: ${key}`)
        CacheManager.remove(key)
        return null
      }
    } catch (error) {
      Logger.error(`Cache get failed: ${key}`, error)
      return null
    }
  }

  /**
   * 删除缓存
   * @param {string} key 缓存键
   */
  static remove(key) {
    try {
      wx.removeStorageSync(key)
      Logger.debug(`Cache removed: ${key}`)
    } catch (error) {
      Logger.error(`Cache remove failed: ${key}`, error)
    }
  }

  /**
   * 清空所有缓存
   */
  static clear() {
    try {
      wx.clearStorageSync()
      Logger.info('All cache cleared')
    } catch (error) {
      Logger.error('Cache clear failed', error)
    }
  }

  /**
   * 获取缓存信息
   */
  static getInfo() {
    try {
      const info = wx.getStorageInfoSync()
      Logger.debug('Cache info', info)
      return info
    } catch (error) {
      Logger.error('Get cache info failed', error)
      return null
    }
  }
}

module.exports = CacheManager