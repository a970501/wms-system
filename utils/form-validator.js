// utils/form-validator.js - 表单验证器
const Logger = require('./logger')

class FormValidator {
  static rules = {
    /**
     * 必填验证
     */
    required: (value, message = '此字段为必填项') => {
      if (value === null || value === undefined || value === '') {
        return message
      }
      if (typeof value === 'string' && value.trim() === '') {
        return message
      }
      return null
    },

    /**
     * 数字验证
     */
    number: (value, message = '请输入有效数字') => {
      if (value === '' || value === null || value === undefined) return null
      if (!/^\d+(\.\d+)?$/.test(String(value))) {
        return message
      }
      return null
    },

    /**
     * 正整数验证
     */
    positiveInteger: (value, message = '请输入正整数') => {
      if (value === '' || value === null || value === undefined) return null
      const num = parseInt(value)
      if (isNaN(num) || num <= 0 || !Number.isInteger(num)) {
        return message
      }
      return null
    },

    /**
     * 范围验证
     */
    range: (value, min, max, message) => {
      if (value === '' || value === null || value === undefined) return null
      const num = parseFloat(value)
      if (isNaN(num) || num < min || num > max) {
        return message || `请输入${min}到${max}之间的数值`
      }
      return null
    },

    /**
     * 最小长度验证
     */
    minLength: (value, length, message) => {
      if (value === null || value === undefined) return null
      if (String(value).length < length) {
        return message || `最少需要${length}个字符`
      }
      return null
    },

    /**
     * 最大长度验证
     */
    maxLength: (value, length, message) => {
      if (value === null || value === undefined) return null
      if (String(value).length > length) {
        return message || `最多允许${length}个字符`
      }
      return null
    },

    /**
     * 正则表达式验证
     */
    pattern: (value, regex, message = '格式不正确') => {
      if (value === '' || value === null || value === undefined) return null
      if (!regex.test(String(value))) {
        return message
      }
      return null
    },

    /**
     * 自定义验证函数
     */
    custom: (value, validator, message = '验证失败') => {
      try {
        if (typeof validator === 'function') {
          const result = validator(value)
          return result === true ? null : (result || message)
        }
        return null
      } catch (error) {
        Logger.error('Custom validator error', error)
        return message
      }
    }
  }

  /**
   * 验证单个字段
   * @param {any} value 字段值
   * @param {Array} fieldRules 验证规则数组
   * @returns {string|null} 错误信息或null
   */
  static validateField(value, fieldRules) {
    if (!Array.isArray(fieldRules)) return null

    for (const rule of fieldRules) {
      const { type, message, ...params } = rule
      const validator = FormValidator.rules[type]
      
      if (!validator) {
        Logger.warn(`Unknown validation rule: ${type}`)
        continue
      }

      let error = null
      switch (type) {
        case 'range':
          error = validator(value, params.min, params.max, message)
          break
        case 'minLength':
        case 'maxLength':
          error = validator(value, params.length, message)
          break
        case 'pattern':
          error = validator(value, params.regex, message)
          break
        case 'custom':
          error = validator(value, params.validator, message)
          break
        default:
          error = validator(value, message)
      }

      if (error) return error
    }

    return null
  }

  /**
   * 验证整个表单
   * @param {Object} data 表单数据
   * @param {Object} rules 验证规则
   * @returns {Object} 验证结果 { isValid: boolean, errors: Object }
   */
  static validate(data, rules) {
    const errors = {}
    let isValid = true

    for (const field in rules) {
      const fieldValue = data[field]
      const fieldRules = rules[field]
      const error = FormValidator.validateField(fieldValue, fieldRules)
      
      if (error) {
        errors[field] = error
        isValid = false
      }
    }

    Logger.debug('Form validation result', { isValid, errors })
    return { isValid, errors }
  }

  /**
   * 获取第一个错误信息
   * @param {Object} errors 错误对象
   * @returns {string|null} 第一个错误信息
   */
  static getFirstError(errors) {
    const errorKeys = Object.keys(errors)
    return errorKeys.length > 0 ? errors[errorKeys[0]] : null
  }

  /**
   * 创建常用验证规则
   */
  static createRules() {
    return {
      // 产品名称
      productName: [
        { type: 'required', message: '请选择产品名称' }
      ],
      
      // 规格
      specification: [
        { type: 'required', message: '请选择规格' }
      ],
      
      // 数量
      quantity: [
        { type: 'required', message: '请输入数量' },
        { type: 'positiveInteger', message: '数量必须为正整数' },
        { type: 'range', min: 1, max: 999999, message: '数量范围为1-999999' }
      ],
      
      // 报废数量
      defectiveQuantity: [
        { type: 'positiveInteger', message: '报废数量必须为正整数' },
        { type: 'range', min: 1, max: 999999, message: '报废数量范围为1-999999' }
      ],
      
      // 用户名
      username: [
        { type: 'required', message: '请输入用户名' },
        { type: 'minLength', length: 2, message: '用户名至少2个字符' },
        { type: 'maxLength', length: 20, message: '用户名最多20个字符' }
      ],
      
      // 密码
      password: [
        { type: 'required', message: '请输入密码' },
        { type: 'minLength', length: 6, message: '密码至少6个字符' }
      ]
    }
  }
}

module.exports = FormValidator