// utils/security-utils.js - 安全工具类
const Logger = require('./logger')

class SecurityUtils {
  /**
   * XSS防护 - 清理HTML标签
   * @param {string} input 输入字符串
   * @returns {string} 清理后的字符串
   */
  static sanitizeInput(input) {
    if (typeof input !== 'string') return input
    
    return input
      .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
      .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '')
      .replace(/javascript:/gi, '')
      .replace(/on\w+\s*=/gi, '')
      .replace(/<[^>]*>/g, '')
  }

  /**
   * 验证数字范围
   * @param {any} value 值
   * @param {number} min 最小值
   * @param {number} max 最大值
   * @returns {boolean} 是否有效
   */
  static validateNumber(value, min = 0, max = 999999) {
    const num = parseFloat(value)
    return !isNaN(num) && num >= min && num <= max && Number.isFinite(num)
  }

  /**
   * 验证整数
   * @param {any} value 值
   * @param {number} min 最小值
   * @param {number} max 最大值
   * @returns {boolean} 是否有效
   */
  static validateInteger(value, min = 0, max = 999999) {
    const num = parseInt(value)
    return Number.isInteger(num) && num >= min && num <= max
  }

  /**
   * 验证字符串长度
   * @param {string} str 字符串
   * @param {number} minLength 最小长度
   * @param {number} maxLength 最大长度
   * @returns {boolean} 是否有效
   */
  static validateStringLength(str, minLength = 0, maxLength = 1000) {
    if (typeof str !== 'string') return false
    return str.length >= minLength && str.length <= maxLength
  }

  /**
   * 验证用户名格式
   * @param {string} username 用户名
   * @returns {boolean} 是否有效
   */
  static validateUsername(username) {
    if (typeof username !== 'string') return false
    // 允许中文、英文、数字、下划线，2-20个字符
    const regex = /^[\u4e00-\u9fa5a-zA-Z0-9_]{2,20}$/
    return regex.test(username)
  }

  /**
   * 验证密码强度
   * @param {string} password 密码
   * @returns {Object} 验证结果
   */
  static validatePassword(password) {
    if (typeof password !== 'string') {
      return { isValid: false, message: '密码格式错误' }
    }

    if (password.length < 6) {
      return { isValid: false, message: '密码至少6个字符' }
    }

    if (password.length > 50) {
      return { isValid: false, message: '密码最多50个字符' }
    }

    // 检查是否包含常见弱密码
    const weakPasswords = ['123456', 'password', '111111', '123123', 'admin']
    if (weakPasswords.includes(password.toLowerCase())) {
      return { isValid: false, message: '密码过于简单，请使用更复杂的密码' }
    }

    return { isValid: true, message: '密码格式正确' }
  }

  /**
   * 过滤敏感信息
   * @param {Object} data 数据对象
   * @param {Array} sensitiveFields 敏感字段列表
   * @returns {Object} 过滤后的数据
   */
  static filterSensitiveData(data, sensitiveFields = ['password', 'token', 'secret']) {
    if (!data || typeof data !== 'object') return data

    const filtered = { ...data }
    sensitiveFields.forEach(field => {
      if (filtered[field]) {
        filtered[field] = '***'
      }
    })

    return filtered
  }

  /**
   * 生成随机字符串
   * @param {number} length 长度
   * @returns {string} 随机字符串
   */
  static generateRandomString(length = 16) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    let result = ''
    for (let i = 0; i < length; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length))
    }
    return result
  }

  /**
   * 微信小程序兼容的Base64编码
   * @param {string} str 要编码的字符串
   * @returns {string} Base64编码结果
   */
  static base64Encode(str) {
    if (typeof wx !== 'undefined' && wx.arrayBufferToBase64) {
      // 微信小程序环境
      const buffer = new ArrayBuffer(str.length)
      const view = new Uint8Array(buffer)
      for (let i = 0; i < str.length; i++) {
        view[i] = str.charCodeAt(i)
      }
      return wx.arrayBufferToBase64(buffer)
    } else if (typeof btoa !== 'undefined') {
      // 浏览器环境
      return btoa(str)
    } else {
      // 手动实现Base64编码
      const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
      let result = ''
      let i = 0
      
      while (i < str.length) {
        const a = str.charCodeAt(i++)
        const b = i < str.length ? str.charCodeAt(i++) : 0
        const c = i < str.length ? str.charCodeAt(i++) : 0
        
        const bitmap = (a << 16) | (b << 8) | c
        
        result += chars.charAt((bitmap >> 18) & 63)
        result += chars.charAt((bitmap >> 12) & 63)
        result += i - 2 < str.length ? chars.charAt((bitmap >> 6) & 63) : '='
        result += i - 1 < str.length ? chars.charAt(bitmap & 63) : '='
      }
      
      return result
    }
  }

  /**
   * 微信小程序兼容的Base64解码
   * @param {string} base64 Base64编码的字符串
   * @returns {string} 解码结果
   */
  static base64Decode(base64) {
    if (typeof wx !== 'undefined' && wx.base64ToArrayBuffer) {
      // 微信小程序环境
      try {
        const buffer = wx.base64ToArrayBuffer(base64)
        const view = new Uint8Array(buffer)
        let result = ''
        for (let i = 0; i < view.length; i++) {
          result += String.fromCharCode(view[i])
        }
        return result
      } catch (error) {
        Logger.error('Base64 decode failed in WeChat', error)
        return base64
      }
    } else if (typeof atob !== 'undefined') {
      // 浏览器环境
      return atob(base64)
    } else {
      // 手动实现Base64解码
      const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
      let result = ''
      let i = 0
      
      base64 = base64.replace(/[^A-Za-z0-9+/]/g, '')
      
      while (i < base64.length) {
        const encoded1 = chars.indexOf(base64.charAt(i++))
        const encoded2 = chars.indexOf(base64.charAt(i++))
        const encoded3 = chars.indexOf(base64.charAt(i++))
        const encoded4 = chars.indexOf(base64.charAt(i++))
        
        const bitmap = (encoded1 << 18) | (encoded2 << 12) | (encoded3 << 6) | encoded4
        
        result += String.fromCharCode((bitmap >> 16) & 255)
        if (encoded3 !== 64) result += String.fromCharCode((bitmap >> 8) & 255)
        if (encoded4 !== 64) result += String.fromCharCode(bitmap & 255)
      }
      
      return result
    }
  }

  /**
   * 简单加密（仅用于本地存储，不可用于传输）
   * @param {string} text 明文
   * @param {string} key 密钥
   * @returns {string} 加密后的文本
   */
  static simpleEncrypt(text, key = 'wms_app_key') {
    if (typeof text !== 'string') return text
    
    let encrypted = ''
    for (let i = 0; i < text.length; i++) {
      const textChar = text.charCodeAt(i)
      const keyChar = key.charCodeAt(i % key.length)
      encrypted += String.fromCharCode(textChar ^ keyChar)
    }
    
    return SecurityUtils.base64Encode(encrypted) // 使用兼容的Base64编码
  }

  /**
   * 简单解密
   * @param {string} encryptedText 加密文本
   * @param {string} key 密钥
   * @returns {string} 解密后的文本
   */
  static simpleDecrypt(encryptedText, key = 'wms_app_key') {
    try {
      const encrypted = SecurityUtils.base64Decode(encryptedText) // 使用兼容的Base64解码
      let decrypted = ''
      
      for (let i = 0; i < encrypted.length; i++) {
        const encryptedChar = encrypted.charCodeAt(i)
        const keyChar = key.charCodeAt(i % key.length)
        decrypted += String.fromCharCode(encryptedChar ^ keyChar)
      }
      
      return decrypted
    } catch (error) {
      Logger.error('Decrypt failed', error)
      return encryptedText
    }
  }

  /**
   * 检查Token是否过期
   * @param {string} token JWT Token
   * @returns {boolean} 是否过期
   */
  static isTokenExpired(token) {
    if (!token || typeof token !== 'string') return true

    try {
      // 简单的JWT过期检查（实际项目中应该用专门的JWT库）
      const parts = token.split('.')
      if (parts.length !== 3) return true

      const payload = JSON.parse(SecurityUtils.base64Decode(parts[1]))
      const now = Math.floor(Date.now() / 1000)
      
      return payload.exp && payload.exp < now
    } catch (error) {
      Logger.error('Token validation failed', error)
      return true
    }
  }

  /**
   * 数据脱敏
   * @param {string} data 原始数据
   * @param {string} type 脱敏类型
   * @returns {string} 脱敏后的数据
   */
  static maskData(data, type = 'default') {
    if (!data || typeof data !== 'string') return data

    switch (type) {
      case 'phone':
        return data.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
      case 'email':
        return data.replace(/(.{2}).*(@.*)/, '$1***$2')
      case 'idcard':
        return data.replace(/(\d{6})\d{8}(\d{4})/, '$1********$2')
      default:
        const len = data.length
        if (len <= 2) return data
        if (len <= 4) return data.charAt(0) + '*'.repeat(len - 2) + data.charAt(len - 1)
        return data.substring(0, 2) + '*'.repeat(len - 4) + data.substring(len - 2)
    }
  }

  /**
   * 防抖函数
   * @param {Function} func 要防抖的函数
   * @param {number} delay 延迟时间
   * @returns {Function} 防抖后的函数
   */
  static debounce(func, delay = 300) {
    let timeoutId
    return function (...args) {
      clearTimeout(timeoutId)
      timeoutId = setTimeout(() => func.apply(this, args), delay)
    }
  }

  /**
   * 节流函数
   * @param {Function} func 要节流的函数
   * @param {number} limit 时间间隔
   * @returns {Function} 节流后的函数
   */
  static throttle(func, limit = 300) {
    let inThrottle
    return function (...args) {
      if (!inThrottle) {
        func.apply(this, args)
        inThrottle = true
        setTimeout(() => inThrottle = false, limit)
      }
    }
  }
}

module.exports = SecurityUtils