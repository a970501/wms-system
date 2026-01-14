// utils/api-service.js - API服务封装
const Logger = require('./logger')
const LoadingManager = require('./loading-manager')
const CacheManager = require('./cache-manager')

class ApiService {
  static retryCount = 3
  static retryDelay = 1000

  /**
   * 统一API请求方法
   * @param {Object} options 请求选项
   * @param {boolean} showLoading 是否显示加载提示
   * @param {boolean} useCache 是否使用缓存
   * @param {number} cacheTime 缓存时间
   * @returns {Promise} 请求结果
   */
  static async request(options, showLoading = true, useCache = false, cacheTime = 5 * 60 * 1000) {
    const app = getApp()
    const cacheKey = useCache ? ApiService.getCacheKey(options) : null

    // 检查缓存
    if (useCache && cacheKey) {
      const cachedData = CacheManager.get(cacheKey)
      if (cachedData) {
        Logger.debug(`API cache hit: ${options.url}`)
        return cachedData
      }
    }

    if (showLoading) {
      LoadingManager.show()
    }

    try {
      const result = await ApiService.requestWithRetry(app, options)
      
      // 缓存成功结果
      if (useCache && cacheKey && result.code === 200) {
        CacheManager.set(cacheKey, result, cacheTime)
      }

      return result
    } catch (error) {
      ApiService.handleError(error, options)
      throw error
    } finally {
      if (showLoading) {
        LoadingManager.hide()
      }
    }
  }

  /**
   * 带重试的请求
   */
  static async requestWithRetry(app, options, retryCount = ApiService.retryCount) {
    try {
      return await app.request(options)
    } catch (error) {
      if (retryCount > 0 && ApiService.shouldRetry(error)) {
        Logger.warn(`API request failed, retrying... (${retryCount} attempts left)`, error)
        await ApiService.delay(ApiService.retryDelay)
        return ApiService.requestWithRetry(app, options, retryCount - 1)
      }
      throw error
    }
  }

  /**
   * 判断是否应该重试
   */
  static shouldRetry(error) {
    // 网络错误或服务器错误时重试
    return error.statusCode >= 500 || error.errMsg?.includes('timeout') || error.errMsg?.includes('fail')
  }

  /**
   * 延迟函数
   */
  static delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms))
  }

  /**
   * 生成缓存键
   */
  static getCacheKey(options) {
    const { url, method = 'GET', data } = options
    const key = `api_${method}_${url}`
    if (data && Object.keys(data).length > 0) {
      return `${key}_${JSON.stringify(data)}`
    }
    return key
  }

  /**
   * 统一错误处理
   */
  static handleError(error, options) {
    Logger.error(`API request failed: ${options.url}`, error)

    if (error.statusCode === 401) {
      // 未授权，跳转登录
      const app = getApp()
      app.logout()
      LoadingManager.showError('登录已过期，请重新登录')
    } else if (error.statusCode === 403) {
      LoadingManager.showError('没有权限访问')
    } else if (error.statusCode === 404) {
      LoadingManager.showError('请求的资源不存在')
    } else if (error.statusCode >= 500) {
      LoadingManager.showError('服务器错误，请稍后重试')
    } else if (error.errMsg?.includes('timeout')) {
      LoadingManager.showError('请求超时，请检查网络连接')
    } else if (error.errMsg?.includes('fail')) {
      LoadingManager.showError('网络连接失败，请检查网络')
    } else if (error.message) {
      LoadingManager.showError(error.message)
    } else {
      LoadingManager.showError('请求失败，请稍后重试')
    }
  }

  /**
   * GET请求
   */
  static get(url, params = {}, options = {}) {
    const queryString = Object.keys(params).length > 0 
      ? '?' + Object.keys(params).map(key => `${key}=${encodeURIComponent(params[key])}`).join('&')
      : ''
    
    return ApiService.request({
      url: url + queryString,
      method: 'GET'
    }, options.showLoading, options.useCache, options.cacheTime)
  }

  /**
   * POST请求
   */
  static post(url, data = {}, options = {}) {
    return ApiService.request({
      url,
      method: 'POST',
      data
    }, options.showLoading)
  }

  /**
   * PUT请求
   */
  static put(url, data = {}, options = {}) {
    return ApiService.request({
      url,
      method: 'PUT',
      data
    }, options.showLoading)
  }

  /**
   * DELETE请求
   */
  static delete(url, options = {}) {
    return ApiService.request({
      url,
      method: 'DELETE'
    }, options.showLoading)
  }

  /**
   * 批量请求
   */
  static async batchRequest(requests, showLoading = true) {
    if (showLoading) {
      LoadingManager.show('批量处理中...')
    }

    try {
      const results = await Promise.allSettled(
        requests.map(req => ApiService.request(req, false))
      )
      
      const successResults = []
      const failedResults = []
      
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          successResults.push({ index, data: result.value })
        } else {
          failedResults.push({ index, error: result.reason })
        }
      })

      Logger.info(`Batch request completed: ${successResults.length} success, ${failedResults.length} failed`)
      
      return { successResults, failedResults }
    } finally {
      if (showLoading) {
        LoadingManager.hide()
      }
    }
  }
}

module.exports = ApiService