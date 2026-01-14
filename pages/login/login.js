// pages/login/login.js
var app = getApp()
var Logger = require('../../utils/logger')
var LoadingManager = require('../../utils/loading-manager')
var FormValidator = require('../../utils/form-validator')
var SecurityUtils = require('../../utils/security-utils')

Page({
  data: {
    username: '',
    password: '',
    showPassword: false,
    loading: false,
    errors: {}
  },

  onLoad() {
    Logger.info('Login page loaded')
    // 如果已经登录，直接跳转到首页
    if (app.globalData.isLoggedIn) {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  onUsernameInput: function(e) {
    var username = SecurityUtils.sanitizeInput(e.detail.value)
    this.setData({ 
      username: username,
      'errors.username': null
    })
    this.validateField('username', username)
  },

  onPasswordInput: function(e) {
    this.setData({ 
      password: e.detail.value,
      'errors.password': null
    })
    this.validateField('password', e.detail.value)
  },

  validateField: function(fieldName, value) {
    var rules = FormValidator.createRules()
    var fieldRules = rules[fieldName]
    
    if (fieldRules) {
      var error = FormValidator.validateField(value, fieldRules)
      if (error) {
        var errorObj = {}
        errorObj['errors.' + fieldName] = error
        this.setData(errorObj)
      }
    }
  },

  togglePassword: function() {
    this.setData({ showPassword: !this.data.showPassword })
  },

  handleLogin: function() {
    var username = this.data.username
    var password = this.data.password

    // 表单验证
    var rules = FormValidator.createRules()
    var validation = FormValidator.validate({ username: username, password: password }, {
      username: rules.username,
      password: rules.password
    })

    if (!validation.isValid) {
      var firstError = FormValidator.getFirstError(validation.errors)
      LoadingManager.showError(firstError)
      this.setData({ errors: validation.errors })
      return
    }

    this.setData({ loading: true, errors: {} })

    app.login(username, password)
      .then(function(res) {
        Logger.info('Login successful')
        if (res.code === 200) {
          LoadingManager.showSuccess('登录成功')
          setTimeout(function() {
            wx.switchTab({ url: '/pages/index/index' })
          }, 500)
        } else {
          LoadingManager.showError(res.message || '登录失败')
        }
      })
      .catch(function(err) {
        Logger.error('Login failed', err)
        var msg = '登录失败'
        if (err.message) {
          msg = err.message
        } else if (err.statusCode === 401) {
          msg = '用户名或密码错误'
        } else if (err.errMsg && err.errMsg.indexOf('timeout') !== -1) {
          msg = '网络超时，请重试'
        } else if (err.errMsg && err.errMsg.indexOf('fail') !== -1) {
          msg = '网络连接失败，请检查网络'
        }
        LoadingManager.showError(msg)
      })
      .finally(function() {
        this.setData({ loading: false })
      }.bind(this))
  },

  // 分享给好友
  onShareAppMessage() {
    return {
      title: '仓库管理系统 - 邀请你一起使用',
      path: '/pages/login/login',
      imageUrl: ''
    }
  }
})

