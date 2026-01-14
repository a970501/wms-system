// utils/state-manager.js - 全局状态管理器
const Logger = require('./logger')

class StateManager {
  constructor() {
    this.state = {}
    this.listeners = {}
    this.middleware = []
  }

  /**
   * 设置状态
   * @param {string} key 状态键
   * @param {any} value 状态值
   * @param {boolean} notify 是否通知监听者
   */
  setState(key, value, notify = true) {
    const oldValue = this.state[key]
    
    // 执行中间件
    for (const middleware of this.middleware) {
      try {
        const result = middleware(key, value, oldValue)
        if (result !== undefined) {
          value = result
        }
      } catch (error) {
        Logger.error(`State middleware error for key: ${key}`, error)
      }
    }

    this.state[key] = value
    Logger.debug(`State updated: ${key}`, { oldValue, newValue: value })

    if (notify) {
      this.notify(key, value, oldValue)
    }
  }

  /**
   * 获取状态
   * @param {string} key 状态键
   * @param {any} defaultValue 默认值
   * @returns {any} 状态值
   */
  getState(key, defaultValue = null) {
    return this.state.hasOwnProperty(key) ? this.state[key] : defaultValue
  }

  /**
   * 删除状态
   * @param {string} key 状态键
   */
  removeState(key) {
    const oldValue = this.state[key]
    delete this.state[key]
    Logger.debug(`State removed: ${key}`, { oldValue })
    this.notify(key, undefined, oldValue)
  }

  /**
   * 清空所有状态
   */
  clearState() {
    const oldState = { ...this.state }
    this.state = {}
    Logger.debug('All state cleared', { oldState })
    
    // 通知所有监听者
    Object.keys(oldState).forEach(key => {
      this.notify(key, undefined, oldState[key])
    })
  }

  /**
   * 订阅状态变化
   * @param {string} key 状态键
   * @param {Function} callback 回调函数
   * @returns {Function} 取消订阅函数
   */
  subscribe(key, callback) {
    if (typeof callback !== 'function') {
      Logger.error('Subscribe callback must be a function')
      return () => {}
    }

    if (!this.listeners[key]) {
      this.listeners[key] = []
    }
    
    this.listeners[key].push(callback)
    Logger.debug(`Subscribed to state: ${key}`)

    // 返回取消订阅函数
    return () => {
      this.unsubscribe(key, callback)
    }
  }

  /**
   * 取消订阅
   * @param {string} key 状态键
   * @param {Function} callback 回调函数
   */
  unsubscribe(key, callback) {
    if (this.listeners[key]) {
      const index = this.listeners[key].indexOf(callback)
      if (index > -1) {
        this.listeners[key].splice(index, 1)
        Logger.debug(`Unsubscribed from state: ${key}`)
      }
    }
  }

  /**
   * 通知监听者
   * @param {string} key 状态键
   * @param {any} newValue 新值
   * @param {any} oldValue 旧值
   */
  notify(key, newValue, oldValue) {
    const listeners = this.listeners[key]
    if (listeners && listeners.length > 0) {
      listeners.forEach(callback => {
        try {
          callback(newValue, oldValue, key)
        } catch (error) {
          Logger.error(`State listener error for key: ${key}`, error)
        }
      })
    }
  }

  /**
   * 添加中间件
   * @param {Function} middleware 中间件函数
   */
  addMiddleware(middleware) {
    if (typeof middleware === 'function') {
      this.middleware.push(middleware)
      Logger.debug('State middleware added')
    }
  }

  /**
   * 批量更新状态
   * @param {Object} updates 更新对象
   */
  batchUpdate(updates) {
    if (!updates || typeof updates !== 'object') return

    Logger.debug('Batch state update started', updates)
    
    Object.keys(updates).forEach(key => {
      this.setState(key, updates[key], false)
    })

    // 批量通知
    Object.keys(updates).forEach(key => {
      this.notify(key, updates[key], this.state[key])
    })

    Logger.debug('Batch state update completed')
  }

  /**
   * 获取所有状态
   * @returns {Object} 状态对象副本
   */
  getAllState() {
    return { ...this.state }
  }

  /**
   * 持久化状态到本地存储
   * @param {Array} keys 要持久化的状态键数组，为空则持久化所有状态
   */
  persistState(keys = null) {
    try {
      const stateToPersist = keys 
        ? keys.reduce((obj, key) => {
            if (this.state.hasOwnProperty(key)) {
              obj[key] = this.state[key]
            }
            return obj
          }, {})
        : this.state

      wx.setStorageSync('app_state', stateToPersist)
      Logger.debug('State persisted', { keys: keys || 'all' })
    } catch (error) {
      Logger.error('Failed to persist state', error)
    }
  }

  /**
   * 从本地存储恢复状态
   * @param {Array} keys 要恢复的状态键数组，为空则恢复所有状态
   */
  restoreState(keys = null) {
    try {
      const persistedState = wx.getStorageSync('app_state') || {}
      
      if (keys) {
        keys.forEach(key => {
          if (persistedState.hasOwnProperty(key)) {
            this.setState(key, persistedState[key])
          }
        })
      } else {
        Object.keys(persistedState).forEach(key => {
          this.setState(key, persistedState[key])
        })
      }

      Logger.debug('State restored', { keys: keys || 'all' })
    } catch (error) {
      Logger.error('Failed to restore state', error)
    }
  }
}

// 创建全局状态管理器实例
const globalStateManager = new StateManager()

// 添加日志中间件
globalStateManager.addMiddleware((key, newValue, oldValue) => {
  // 过滤敏感信息
  const sensitiveKeys = ['token', 'password', 'userInfo']
  if (sensitiveKeys.includes(key)) {
    Logger.debug(`State change: ${key} = [SENSITIVE DATA]`)
  }
  return newValue
})

module.exports = {
  StateManager,
  globalStateManager
}