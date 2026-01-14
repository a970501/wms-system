// utils/loading-manager.js - 加载状态管理器
const Logger = require('./logger')

class LoadingManager {
  static loadingCount = 0
  static loadingTimer = null

  /**
   * 显示加载提示
   * @param {string} title 提示文字
   * @param {boolean} mask 是否显示透明蒙层
   */
  static show(title = '加载中...', mask = true) {
    try {
      LoadingManager.loadingCount++
      
      // 防止重复调用
      if (LoadingManager.loadingCount === 1) {
        wx.showLoading({ title, mask })
        Logger.debug(`Loading shown: ${title}`)
        
        // 设置超时自动隐藏
        LoadingManager.loadingTimer = setTimeout(() => {
          LoadingManager.forceHide()
          Logger.warn('Loading timeout, force hide')
        }, 30000) // 30秒超时
      }
    } catch (error) {
      Logger.error('Show loading failed', error)
    }
  }

  /**
   * 隐藏加载提示
   */
  static hide() {
    try {
      LoadingManager.loadingCount = Math.max(0, LoadingManager.loadingCount - 1)
      
      if (LoadingManager.loadingCount === 0) {
        wx.hideLoading()
        if (LoadingManager.loadingTimer) {
          clearTimeout(LoadingManager.loadingTimer)
          LoadingManager.loadingTimer = null
        }
        Logger.debug('Loading hidden')
      }
    } catch (error) {
      Logger.error('Hide loading failed', error)
    }
  }

  /**
   * 强制隐藏加载提示
   */
  static forceHide() {
    try {
      LoadingManager.loadingCount = 0
      wx.hideLoading()
      if (LoadingManager.loadingTimer) {
        clearTimeout(LoadingManager.loadingTimer)
        LoadingManager.loadingTimer = null
      }
      Logger.debug('Loading force hidden')
    } catch (error) {
      Logger.error('Force hide loading failed', error)
    }
  }

  /**
   * 显示提示消息
   * @param {string} title 提示文字
   * @param {string} icon 图标类型
   * @param {number} duration 显示时长
   */
  static showToast(title, icon = 'success', duration = 2000) {
    try {
      wx.showToast({ title, icon, duration })
      Logger.debug(`Toast shown: ${title}`)
    } catch (error) {
      Logger.error('Show toast failed', error)
    }
  }

  /**
   * 显示错误提示
   * @param {string} message 错误信息
   */
  static showError(message) {
    LoadingManager.showToast(message, 'none', 3000)
  }

  /**
   * 显示成功提示
   * @param {string} message 成功信息
   */
  static showSuccess(message) {
    LoadingManager.showToast(message, 'success', 2000)
  }

  /**
   * 显示警告提示
   * @param {string} message 警告信息
   */
  static showWarning(message) {
    LoadingManager.showToast(message, 'none', 2500)
  }
}

module.exports = LoadingManager