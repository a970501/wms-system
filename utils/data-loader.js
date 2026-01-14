// utils/data-loader.js - 通用数据加载器
const Logger = require('./logger')
const SmartCacheManager = require('./smart-cache-manager')
const OfflineManager = require('./offline-manager')
const LoadingManager = require('./loading-manager')

class DataLoader {
  /**
   * 分页数据加载器
   * @param {string} url API地址
   * @param {Object} options 配置选项
   * @returns {Promise} 加载结果
   */
  static async loadPaginated(url, options) {
    options = options || {}
    const page = options.page || 0
    const size = options.size || 20
    const cache = options.cache !== false
    const cacheStrategy = options.cacheStrategy || SmartCacheManager.CACHE_STRATEGIES.DYNAMIC
    const cacheTime = options.cacheTime || null
    const showLoading = options.showLoading || false
    const loadingText = options.loadingText || '加载中...'
    const params = options.params || {}
    const transform = options.transform || null
    const enableOffline = options.enableOffline !== false

    const app = getApp()
    
    // 构建查询参数
    const queryParamsObj = Object.assign({
      page: page.toString(),
      size: size.toString()
    }, params)
    
    const queryParams = Object.keys(queryParamsObj).map(function(key) {
      return key + '=' + encodeURIComponent(queryParamsObj[key])
    }).join('&')
    
    const fullUrl = url + '?' + queryParams
    const cacheKey = 'paginated_' + url + '_' + page + '_' + size + '_' + JSON.stringify(params)

    try {
      if (showLoading) {
        LoadingManager.show(loadingText)
      }

      // 尝试从缓存获取
      if (cache) {
        const cachedData = SmartCacheManager.getWithStats(cacheKey)
        if (cachedData !== null) {
      Logger.debug('Data loaded from cache', { url: url, page: page, size: size })
          return cachedData
        }
      }

      // 发起网络请求
      const response = await OfflineManager.createOfflineRequest(app, {
        url: fullUrl,
        method: 'GET'
      }, enableOffline)

      let data = response.data || {}

      // 数据转换
      if (transform && typeof transform === 'function') {
        data = transform(data)
      }

      // 标准化分页数据格式
      const result = {
        content: Array.isArray(data.content) ? data.content : (Array.isArray(data) ? data : []),
        totalElements: data.totalElements || data.total || 0,
        totalPages: data.totalPages || Math.ceil((data.total || 0) / size),
        currentPage: page,
        pageSize: size,
        hasNext: data.hasNext || (page + 1) * size < (data.total || 0),
        hasPrevious: page > 0
      }

      // 缓存结果
      if (cache) {
        const expireTime = cacheTime || SmartCacheManager.calculateExpireTime(cacheKey, cacheStrategy)
        SmartCacheManager.setWithStrategy(cacheKey, result, cacheStrategy, { customExpireTime: expireTime })
      }

      Logger.debug('Paginated data loaded', { 
        url: url, 
        page: page, 
        size: size, 
        totalElements: result.totalElements,
        contentLength: result.content.length
      })

      return result

    } catch (error) {
      Logger.error('Failed to load paginated data', { url: url, page: page, size: size, error: error })
      throw error
    } finally {
      if (showLoading) {
        LoadingManager.hide()
      }
    }
  }

  /**
   * 单个数据项加载器
   * @param {string} url API地址
   * @param {Object} options 配置选项
   * @returns {Promise} 加载结果
   */
  static async loadSingle(url, options = {}) {
    const {
      cache = true,
      cacheStrategy = SmartCacheManager.CACHE_STRATEGIES.ADAPTIVE,
      cacheTime = null,
      showLoading = false,
      loadingText = '加载中...',
      transform = null,
      enableOffline = true,
      defaultValue = null
    } = options

    const app = getApp()
    const cacheKey = 'single_' + url

    try {
      if (showLoading) {
        LoadingManager.show(loadingText)
      }

      // 尝试从缓存获取
      if (cache) {
        const cachedData = SmartCacheManager.getWithStats(cacheKey)
        if (cachedData !== null) {
          Logger.debug('Single data loaded from cache', { url })
          return cachedData
        }
      }

      // 发起网络请求
      const response = await OfflineManager.createOfflineRequest(app, {
        url,
        method: 'GET'
      }, enableOffline)

      let data = response.data

      // 数据转换
      if (transform && typeof transform === 'function') {
        data = transform(data)
      }

      // 缓存结果
      if (cache) {
        const expireTime = cacheTime || SmartCacheManager.calculateExpireTime(cacheKey, cacheStrategy)
        SmartCacheManager.setWithStrategy(cacheKey, data, cacheStrategy, { customExpireTime: expireTime })
      }

      Logger.debug('Single data loaded', { url, dataType: typeof data })

      return data

    } catch (error) {
      Logger.error('Failed to load single data', { url, error })
      
      // 返回默认值而不是抛出错误
      if (defaultValue !== null) {
        Logger.info('Returning default value due to load failure', { url, defaultValue })
        return defaultValue
      }
      
      throw error
    } finally {
      if (showLoading) {
        LoadingManager.hide()
      }
    }
  }

  /**
   * 列表数据加载器
   * @param {string} url API地址
   * @param {Object} options 配置选项
   * @returns {Promise} 加载结果
   */
  static async loadList(url, options) {
    options = options || {}
    const cache = options.cache !== false
    const cacheStrategy = options.cacheStrategy || SmartCacheManager.CACHE_STRATEGIES.DYNAMIC
    const cacheTime = options.cacheTime || null
    const showLoading = options.showLoading || false
    const loadingText = options.loadingText || '加载中...'
    const params = options.params || {}
    const transform = options.transform || null
    const enableOffline = options.enableOffline !== false
    const maxItems = options.maxItems || null

    const app = getApp()
    
    // 手动构建查询参数
    let queryParams = ''
    if (Object.keys(params).length > 0) {
      const paramPairs = Object.keys(params).map(function(key) {
        return key + '=' + encodeURIComponent(params[key])
      })
      queryParams = '?' + paramPairs.join('&')
    }
    
    const fullUrl = url + queryParams
    const cacheKey = 'list_' + url + '_' + JSON.stringify(params)

    try {
      if (showLoading) {
        LoadingManager.show(loadingText)
      }

      // 尝试从缓存获取
      if (cache) {
        const cachedData = SmartCacheManager.getWithStats(cacheKey)
        if (cachedData !== null) {
          Logger.debug('List data loaded from cache', { url: url, length: cachedData.length })
          return cachedData
        }
      }

      // 发起网络请求
      const response = await OfflineManager.createOfflineRequest(app, {
        url: fullUrl,
        method: 'GET'
      }, enableOffline)

      let data = response.data

      // 确保返回数组
      if (!Array.isArray(data)) {
        data = data.content || data.items || data.list || []
      }

      // 限制最大项数
      if (maxItems && data.length > maxItems) {
        data = data.slice(0, maxItems)
      }

      // 数据转换
      if (transform && typeof transform === 'function') {
        data = data.map(transform)
      }

      // 缓存结果
      if (cache) {
        const expireTime = cacheTime || SmartCacheManager.calculateExpireTime(cacheKey, cacheStrategy)
        SmartCacheManager.setWithStrategy(cacheKey, data, cacheStrategy, { customExpireTime: expireTime })
      }

      Logger.debug('List data loaded', { url, length: data.length })

      return data

    } catch (error) {
      Logger.error('Failed to load list data', { url, error })
      throw error
    } finally {
      if (showLoading) {
        LoadingManager.hide()
      }
    }
  }

  /**
   * 批量数据加载器
   * @param {Array} requests 请求配置数组
   * @param {Object} options 配置选项
   * @returns {Promise} 加载结果
   */
  static async loadBatch(requests, options = {}) {
    const {
      concurrent = true,
      showLoading = false,
      loadingText = '批量加载中...',
      failFast = false
    } = options

    try {
      if (showLoading) {
        LoadingManager.show(loadingText)
      }

      Logger.info('Starting batch data loading', { requestCount: requests.length, concurrent })

      let results

      if (concurrent) {
        // 并发加载
        if (failFast) {
          results = await Promise.all(requests.map(req => this.executeRequest(req)))
        } else {
          results = await Promise.allSettled(requests.map(req => this.executeRequest(req)))
          results = results.map((result, index) => {
            if (result.status === 'fulfilled') {
              return result.value
            } else {
              Logger.error('Batch request ' + index + ' failed', result.reason)
              return null
            }
          })
        }
      } else {
        // 顺序加载
        results = []
        for (let i = 0; i < requests.length; i++) {
          try {
            const result = await this.executeRequest(requests[i])
            results.push(result)
          } catch (error) {
            Logger.error('Sequential request ' + i + ' failed', error)
            if (failFast) {
              throw error
            }
            results.push(null)
          }
        }
      }

      Logger.info('Batch data loading completed', { 
        requestCount: requests.length,
        successCount: results.filter(r => r !== null).length
      })

      return results

    } catch (error) {
      Logger.error('Batch data loading failed', error)
      throw error
    } finally {
      if (showLoading) {
        LoadingManager.hide()
      }
    }
  }

  /**
   * 执行单个请求
   * @param {Object} request 请求配置
   * @returns {Promise} 请求结果
   */
  static async executeRequest(request) {
    const type = request.type || 'single'
    const url = request.url
    const options = request.options || {}

    switch (type) {
      case 'paginated':
        return await this.loadPaginated(url, options)
      case 'list':
        return await this.loadList(url, options)
      case 'single':
      default:
        return await this.loadSingle(url, options)
    }
  }

  /**
   * 清除相关缓存
   * @param {string} pattern 缓存键模式
   */
  static clearCache(pattern) {
    try {
      // 这里需要扩展SmartCacheManager来支持模式匹配清除
      Logger.info('Cache clearing requested', { pattern })
      
      // 临时实现：清除所有缓存
      SmartCacheManager.clear()
      
    } catch (error) {
      Logger.error('Failed to clear cache', error)
    }
  }

  /**
   * 预加载数据
   * @param {Array} preloadConfigs 预加载配置
   */
  static async preloadData(preloadConfigs) {
    Logger.info('Starting data preloading', { configCount: preloadConfigs.length })

    const preloadPromises = preloadConfigs.map(async (config) => {
      try {
        const type = config.type || 'single'
        const url = config.url
        const options = config.options || {}
        
        // 设置为后台加载，不显示loading
        const preloadOptions = {
          ...options,
          showLoading: false,
          cache: true
        }

        await this.executeRequest({ type, url, options: preloadOptions })
        Logger.debug('Data preloaded successfully', { url })
        
      } catch (error) {
        Logger.error('Data preload failed', { url: config.url, error })
      }
    })

    await Promise.allSettled(preloadPromises)
    Logger.info('Data preloading completed')
  }
}

module.exports = DataLoader