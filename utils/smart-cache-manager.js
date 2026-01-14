// utils/smart-cache-manager.js - 智能缓存管理器
const Logger = require('./logger')
const CacheManager = require('./cache-manager')

class SmartCacheManager extends CacheManager {
  static CACHE_STRATEGIES = {
    STATIC: 'static',       // 静态数据 - 24小时
    DYNAMIC: 'dynamic',     // 动态数据 - 5分钟
    REALTIME: 'realtime',   // 实时数据 - 30秒
    ADAPTIVE: 'adaptive'    // 自适应 - 根据访问频率调整
  }

  static CACHE_TIMES = {
    [SmartCacheManager.CACHE_STRATEGIES.STATIC]: 24 * 60 * 60 * 1000,  // 24小时
    [SmartCacheManager.CACHE_STRATEGIES.DYNAMIC]: 5 * 60 * 1000,       // 5分钟
    [SmartCacheManager.CACHE_STRATEGIES.REALTIME]: 30 * 1000,          // 30秒
    [SmartCacheManager.CACHE_STRATEGIES.ADAPTIVE]: 10 * 60 * 1000      // 10分钟默认
  }

  static ACCESS_STATS_KEY = 'cache_access_stats'

  /**
   * 智能缓存设置
   * @param {string} key 缓存键
   * @param {any} data 缓存数据
   * @param {string} strategy 缓存策略
   * @param {Object} options 额外选项
   */
  static setWithStrategy(key, data, strategy = SmartCacheManager.CACHE_STRATEGIES.ADAPTIVE, options = {}) {
    const expireTime = SmartCacheManager.calculateExpireTime(key, strategy, options)
    
    // 记录缓存设置
    SmartCacheManager.recordCacheAccess(key, 'set')
    
    // 使用父类方法设置缓存
    super.set(key, data, expireTime)
    
    Logger.debug(`Smart cache set: ${key}`, { 
      strategy, 
      expireTime: expireTime / 1000 + 's' 
    })
  }

  /**
   * 智能缓存获取
   * @param {string} key 缓存键
   * @returns {any|null} 缓存数据或null
   */
  static getWithStats(key) {
    const data = super.get(key)
    
    if (data !== null) {
      SmartCacheManager.recordCacheAccess(key, 'hit')
      Logger.debug(`Smart cache hit: ${key}`)
    } else {
      SmartCacheManager.recordCacheAccess(key, 'miss')
      Logger.debug(`Smart cache miss: ${key}`)
    }
    
    return data
  }

  /**
   * 计算缓存过期时间
   * @param {string} key 缓存键
   * @param {string} strategy 缓存策略
   * @param {Object} options 额外选项
   * @returns {number} 过期时间（毫秒）
   */
  static calculateExpireTime(key, strategy, options = {}) {
    // 如果指定了自定义过期时间，直接使用
    if (options.customExpireTime) {
      return options.customExpireTime
    }

    switch (strategy) {
      case SmartCacheManager.CACHE_STRATEGIES.STATIC:
        // 静态数据：用户信息、配置等
        return SmartCacheManager.CACHE_TIMES.STATIC

      case SmartCacheManager.CACHE_STRATEGIES.DYNAMIC:
        // 动态数据：计件记录、库存等
        return SmartCacheManager.CACHE_TIMES.DYNAMIC

      case SmartCacheManager.CACHE_STRATEGIES.REALTIME:
        // 实时数据：在线状态、通知等
        return SmartCacheManager.CACHE_TIMES.REALTIME

      case SmartCacheManager.CACHE_STRATEGIES.ADAPTIVE:
        // 自适应：根据访问频率调整
        return SmartCacheManager.calculateAdaptiveExpireTime(key)

      default:
        return SmartCacheManager.CACHE_TIMES.ADAPTIVE
    }
  }

  /**
   * 计算自适应过期时间
   * @param {string} key 缓存键
   * @returns {number} 过期时间（毫秒）
   */
  static calculateAdaptiveExpireTime(key) {
    const stats = SmartCacheManager.getCacheStats(key)
    
    if (!stats || stats.accessCount < 5) {
      // 访问次数少，使用默认时间
      return SmartCacheManager.CACHE_TIMES.ADAPTIVE
    }

    const accessCount = stats.accessCount
    const hitRate = stats.hitRate
    const avgAccessInterval = stats.avgAccessInterval
    
    // 根据命中率和访问频率调整缓存时间
    let multiplier = 1
    
    // 高命中率 -> 延长缓存时间
    if (hitRate > 0.8) {
      multiplier *= 2
    } else if (hitRate < 0.3) {
      multiplier *= 0.5
    }
    
    // 高访问频率 -> 延长缓存时间
    if (avgAccessInterval < 60 * 1000) { // 1分钟内
      multiplier *= 1.5
    } else if (avgAccessInterval > 10 * 60 * 1000) { // 10分钟外
      multiplier *= 0.7
    }
    
    const adaptiveTime = SmartCacheManager.CACHE_TIMES.ADAPTIVE * multiplier
    
    // 限制在合理范围内
    return Math.max(30 * 1000, Math.min(adaptiveTime, 60 * 60 * 1000)) // 30秒 - 1小时
  }

  /**
   * 记录缓存访问统计
   * @param {string} key 缓存键
   * @param {string} type 访问类型：set, hit, miss
   */
  static recordCacheAccess(key, type) {
    try {
      const allStats = wx.getStorageSync(SmartCacheManager.ACCESS_STATS_KEY) || {}
      const stats = allStats[key] || {
        accessCount: 0,
        hitCount: 0,
        missCount: 0,
        setCount: 0,
        lastAccess: 0,
        accessTimes: []
      }

      const now = Date.now()
      stats.lastAccess = now
      stats.accessTimes.push(now)
      
      // 只保留最近50次访问记录
      if (stats.accessTimes.length > 50) {
        stats.accessTimes = stats.accessTimes.slice(-50)
      }

      switch (type) {
        case 'set':
          stats.setCount++
          break
        case 'hit':
          stats.accessCount++
          stats.hitCount++
          break
        case 'miss':
          stats.accessCount++
          stats.missCount++
          break
      }

      allStats[key] = stats
      wx.setStorageSync(SmartCacheManager.ACCESS_STATS_KEY, allStats)
      
    } catch (error) {
      Logger.error('Failed to record cache access', error)
    }
  }

  /**
   * 获取缓存统计信息
   * @param {string} key 缓存键
   * @returns {Object|null} 统计信息
   */
  static getCacheStats(key) {
    try {
      const allStats = wx.getStorageSync(SmartCacheManager.ACCESS_STATS_KEY) || {}
      const stats = allStats[key]
      
      if (!stats || stats.accessCount === 0) {
        return null
      }

      // 计算命中率
      const hitRate = stats.hitCount / stats.accessCount
      
      // 计算平均访问间隔
      let avgAccessInterval = 0
      if (stats.accessTimes.length > 1) {
        const intervals = []
        for (let i = 1; i < stats.accessTimes.length; i++) {
          intervals.push(stats.accessTimes[i] - stats.accessTimes[i - 1])
        }
        avgAccessInterval = intervals.reduce((sum, interval) => sum + interval, 0) / intervals.length
      }

      return Object.assign({}, stats, {
        hitRate: hitRate,
        avgAccessInterval: avgAccessInterval
      })
      
    } catch (error) {
      Logger.error('Failed to get cache stats', error)
      return null
    }
  }

  /**
   * 获取所有缓存统计报告
   * @returns {Object} 统计报告
   */
  static getCacheReport() {
    try {
      const allStats = wx.getStorageSync(SmartCacheManager.ACCESS_STATS_KEY) || {}
      const report = {
        totalKeys: Object.keys(allStats).length,
        totalAccess: 0,
        totalHits: 0,
        totalMisses: 0,
        overallHitRate: 0,
        topKeys: []
      }

      const keyStats = []
      
      Object.keys(allStats).forEach(key => {
        const stats = SmartCacheManager.getCacheStats(key)
        if (stats) {
          report.totalAccess += stats.accessCount
          report.totalHits += stats.hitCount
          report.totalMisses += stats.missCount
          
          keyStats.push({
            key,
            accessCount: stats.accessCount,
            hitRate: stats.hitRate,
            avgAccessInterval: stats.avgAccessInterval
          })
        }
      })

      // 计算总体命中率
      if (report.totalAccess > 0) {
        report.overallHitRate = report.totalHits / report.totalAccess
      }

      // 按访问次数排序，获取热门缓存键
      report.topKeys = keyStats
        .sort((a, b) => b.accessCount - a.accessCount)
        .slice(0, 10)

      return report
      
    } catch (error) {
      Logger.error('Failed to generate cache report', error)
      return null
    }
  }

  /**
   * 清理过期统计数据
   * @param {number} maxAge 最大保留时间（毫秒）
   */
  static cleanupStats(maxAge = 7 * 24 * 60 * 60 * 1000) { // 默认7天
    try {
      const allStats = wx.getStorageSync(SmartCacheManager.ACCESS_STATS_KEY) || {}
      const now = Date.now()
      const cleanedStats = {}

      Object.keys(allStats).forEach(key => {
        const stats = allStats[key]
        if (stats.lastAccess && (now - stats.lastAccess) < maxAge) {
          cleanedStats[key] = stats
        }
      })

      wx.setStorageSync(SmartCacheManager.ACCESS_STATS_KEY, cleanedStats)
      
      const removedCount = Object.keys(allStats).length - Object.keys(cleanedStats).length
      Logger.info(`Cache stats cleanup completed`, { removedCount })
      
    } catch (error) {
      Logger.error('Failed to cleanup cache stats', error)
    }
  }

  /**
   * 预热缓存 - 预加载常用数据
   * @param {Array} preloadKeys 预加载的缓存键配置
   */
  static async preloadCache(preloadKeys = []) {
    Logger.info('Starting cache preload', { keyCount: preloadKeys.length })
    
    for (const config of preloadKeys) {
      try {
        const key = config.key
        const loader = config.loader
        const strategy = config.strategy || 'adaptive'
        
        // 检查是否已有有效缓存
        const existing = SmartCacheManager.getWithStats(key)
        if (existing !== null) {
          Logger.debug(`Cache preload skipped (exists): ${key}`)
          continue
        }

        // 执行加载器获取数据
        if (typeof loader === 'function') {
          const data = await loader()
          SmartCacheManager.setWithStrategy(key, data, strategy)
          Logger.debug(`Cache preloaded: ${key}`)
        }
        
      } catch (error) {
        Logger.error(`Cache preload failed: ${config.key}`, error)
      }
    }
    
    Logger.info('Cache preload completed')
  }
}

module.exports = SmartCacheManager