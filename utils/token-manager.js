// utils/token-manager.js - Token自动管理器
const Logger = require('./logger')
const SecurityUtils = require('./security-utils')

class TokenManager {
  static TOKEN_KEY = 'token'
  static REFRESH_TOKEN_KEY = 'refresh_token'
  static TOKEN_REFRESH_BUFFER = 5 * 60 * 1000 // 提前5分钟刷新
  static MAX_RETRY_COUNT = 3
  static isRefreshing = false
  static refreshPromise = null

  /**
   * 检查Token是否需要刷新
   * @param {string} token JWT Token
   * @param {number} bufferTime 缓冲时间（毫秒）
   * @returns {boolean} 是否需要刷新
   */
  static isTokenNearExpiry(token, bufferTime = TokenManager.TOKEN_REFRESH_BUFFER) {
    if (!token || typeof token !== 'string') return true

    try {
      const parts = token.split('.')
      if (parts.length !== 3) return true

      const payload = JSON.parse(SecurityUtils.base64Decode(parts[1]))
      if (!payload.exp) return false // 没有过期时间，不需要刷新

      const expiryTime = payload.exp * 1000 // 转换为毫秒
      const currentTime = Date.now()
      const timeUntilExpiry = expiryTime - currentTime

      Logger.debug('Token expiry check', {
        timeUntilExpiry: Math.floor(timeUntilExpiry / 1000) + 's',
        bufferTime: Math.floor(bufferTime / 1000) + 's',
        needsRefresh: timeUntilExpiry < bufferTime
      })

      return timeUntilExpiry < bufferTime
    } catch (error) {
      Logger.error('Token expiry check failed', error)
      return true
    }
  }

  /**
   * 解析JWT Token
   * @param {string} token JWT Token
   * @returns {Object|null} Token载荷
   */
  static parseJWT(token) {
    try {
      const parts = token.split('.')
      if (parts.length !== 3) return null

      const payload = JSON.parse(SecurityUtils.base64Decode(parts[1]))
      return payload
    } catch (error) {
      Logger.error('JWT parse failed', error)
      return null
    }
  }

  /**
   * 自动刷新Token（如果需要）
   * @returns {Promise<boolean>} 是否成功刷新
   */
  static async refreshTokenIfNeeded() {
    const token = wx.getStorageSync(TokenManager.TOKEN_KEY)
    
    if (!token) {
      Logger.debug('No token found, skipping refresh')
      return false
    }

    if (!TokenManager.isTokenNearExpiry(token)) {
      Logger.debug('Token not near expiry, skipping refresh')
      return true
    }

    // 防止并发刷新
    if (TokenManager.isRefreshing) {
      Logger.debug('Token refresh already in progress, waiting...')
      return await TokenManager.refreshPromise
    }

    TokenManager.isRefreshing = true
    TokenManager.refreshPromise = TokenManager.performTokenRefresh()

    try {
      const result = await TokenManager.refreshPromise
      return result
    } finally {
      TokenManager.isRefreshing = false
      TokenManager.refreshPromise = null
    }
  }

  /**
   * 执行Token刷新
   * @returns {Promise<boolean>} 是否成功刷新
   */
  static async performTokenRefresh() {
    const app = getApp()
    const refreshToken = wx.getStorageSync(TokenManager.REFRESH_TOKEN_KEY)
    
    if (!refreshToken) {
      Logger.warn('No refresh token available')
      return false
    }

    Logger.info('Starting token refresh')

    try {
      const response = await app.request({
        url: '/auth/refresh',
        method: 'POST',
        data: { refreshToken }
      })

      if (response.code === 200 && response.data) {
        const { token: newToken, refreshToken: newRefreshToken } = response.data

        if (newToken) {
          // 保存新Token
          wx.setStorageSync(TokenManager.TOKEN_KEY, newToken)
          app.globalData.token = newToken

          // 更新refresh token（如果提供）
          if (newRefreshToken) {
            wx.setStorageSync(TokenManager.REFRESH_TOKEN_KEY, newRefreshToken)
          }

          Logger.info('Token refreshed successfully')
          return true
        }
      }

      Logger.error('Token refresh failed: invalid response', response)
      return false

    } catch (error) {
      Logger.error('Token refresh request failed', error)
      
      // 如果是401错误，说明refresh token也过期了
      if (error.statusCode === 401) {
        Logger.warn('Refresh token expired, clearing all tokens')
        TokenManager.clearTokens()
        
        // 跳转到登录页
        wx.redirectTo({ url: '/pages/login/login' })
      }
      
      return false
    }
  }

  /**
   * 清除所有Token
   */
  static clearTokens() {
    try {
      wx.removeStorageSync(TokenManager.TOKEN_KEY)
      wx.removeStorageSync(TokenManager.REFRESH_TOKEN_KEY)
      
      const app = getApp()
      app.globalData.token = ''
      app.globalData.isLoggedIn = false
      
      Logger.info('All tokens cleared')
    } catch (error) {
      Logger.error('Failed to clear tokens', error)
    }
  }

  /**
   * 保存Token信息
   * @param {string} token 访问Token
   * @param {string} refreshToken 刷新Token
   */
  static saveTokens(token, refreshToken) {
    try {
      wx.setStorageSync(TokenManager.TOKEN_KEY, token)
      
      if (refreshToken) {
        wx.setStorageSync(TokenManager.REFRESH_TOKEN_KEY, refreshToken)
      }

      const app = getApp()
      app.globalData.token = token
      app.globalData.isLoggedIn = true

      Logger.info('Tokens saved successfully')
    } catch (error) {
      Logger.error('Failed to save tokens', error)
    }
  }

  /**
   * 获取当前有效Token
   * @returns {Promise<string|null>} 有效的Token或null
   */
  static async getValidToken() {
    // 先尝试刷新Token
    const refreshed = await TokenManager.refreshTokenIfNeeded()
    
    if (!refreshed) {
      return null
    }

    const token = wx.getStorageSync(TokenManager.TOKEN_KEY)
    
    // 再次检查Token是否有效
    if (SecurityUtils.isTokenExpired(token)) {
      Logger.warn('Token still expired after refresh attempt')
      return null
    }

    return token
  }

  /**
   * 启动Token监控
   * 定期检查Token状态并自动刷新
   */
  static startTokenMonitoring() {
    // 每分钟检查一次Token状态
    const checkInterval = 60 * 1000 // 1分钟

    const checkToken = async () => {
      try {
        await TokenManager.refreshTokenIfNeeded()
      } catch (error) {
        Logger.error('Token monitoring check failed', error)
      }
    }

    // 立即执行一次检查
    checkToken()

    // 设置定时检查
    setInterval(checkToken, checkInterval)
    
    Logger.info('Token monitoring started', { 
      checkInterval: checkInterval / 1000 + 's' 
    })
  }

  /**
   * 获取Token信息
   * @returns {Object} Token信息
   */
  static getTokenInfo() {
    const token = wx.getStorageSync(TokenManager.TOKEN_KEY)
    const refreshToken = wx.getStorageSync(TokenManager.REFRESH_TOKEN_KEY)
    
    if (!token) {
      return {
        hasToken: false,
        hasRefreshToken: !!refreshToken,
        isExpired: true,
        isNearExpiry: true
      }
    }

    const payload = TokenManager.parseJWT(token)
    const isExpired = SecurityUtils.isTokenExpired(token)
    const isNearExpiry = TokenManager.isTokenNearExpiry(token)

    return {
      hasToken: true,
      hasRefreshToken: !!refreshToken,
      isExpired,
      isNearExpiry,
      expiryTime: payload?.exp ? new Date(payload.exp * 1000) : null,
      username: payload?.username || payload?.sub,
      roles: payload?.roles || []
    }
  }

  /**
   * 手动刷新Token
   * @returns {Promise<boolean>} 是否成功刷新
   */
  static async manualRefresh() {
    Logger.info('Manual token refresh requested')
    
    TokenManager.isRefreshing = false // 重置状态，允许手动刷新
    
    return await TokenManager.refreshTokenIfNeeded()
  }
}

module.exports = TokenManager