// pages/change-password/change-password.js
var app = getApp()
var Logger = require('../../utils/logger')
var SecurityUtils = require('../../utils/security-utils')

Page({
  data: {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    showOldPassword: false,
    showNewPassword: false,
    showConfirmPassword: false,
    loading: false
  },

  onLoad: function() {
    if (!app.checkLogin()) return
  },

  // 输入旧密码
  onOldPasswordInput: function(e) {
    this.setData({ oldPassword: e.detail.value })
  },

  // 输入新密码
  onNewPasswordInput: function(e) {
    this.setData({ newPassword: e.detail.value })
  },

  // 输入确认密码
  onConfirmPasswordInput: function(e) {
    this.setData({ confirmPassword: e.detail.value })
  },

  // 切换旧密码显示
  toggleOldPassword: function() {
    this.setData({ showOldPassword: !this.data.showOldPassword })
  },

  // 切换新密码显示
  toggleNewPassword: function() {
    this.setData({ showNewPassword: !this.data.showNewPassword })
  },

  // 切换确认密码显示
  toggleConfirmPassword: function() {
    this.setData({ showConfirmPassword: !this.data.showConfirmPassword })
  },

  // 验证表单
  validateForm: function() {
    var oldPassword = this.data.oldPassword.trim()
    var newPassword = this.data.newPassword.trim()
    var confirmPassword = this.data.confirmPassword.trim()

    // 检查必填
    if (!oldPassword) {
      wx.showToast({ title: '请输入旧密码', icon: 'none' })
      return false
    }
    if (!newPassword) {
      wx.showToast({ title: '请输入新密码', icon: 'none' })
      return false
    }
    if (!confirmPassword) {
      wx.showToast({ title: '请确认新密码', icon: 'none' })
      return false
    }

    // 使用 SecurityUtils 验证密码强度
    var passwordValidation = SecurityUtils.validatePassword(newPassword)
    if (!passwordValidation.isValid) {
      wx.showToast({ title: passwordValidation.message, icon: 'none' })
      return false
    }

    // 检查两次密码是否一致
    if (newPassword !== confirmPassword) {
      wx.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
      return false
    }

    // 检查新旧密码是否相同
    if (oldPassword === newPassword) {
      wx.showToast({ title: '新密码不能与旧密码相同', icon: 'none' })
      return false
    }

    return true
  },

  // 提交修改密码
  handleSubmit: function() {
    var self = this

    if (!this.validateForm()) return
    if (this.data.loading) return

    this.setData({ loading: true })

    Logger.info('用户请求修改密码')

    app.request({
      url: '/auth/change-password',
      method: 'POST',
      data: {
        oldPassword: this.data.oldPassword.trim(),
        newPassword: this.data.newPassword.trim()
      }
    }).then(function(res) {
      Logger.info('密码修改成功')
      wx.showModal({
        title: '成功',
        content: '密码修改成功，请重新登录',
        showCancel: false,
        success: function() {
          // 退出登录并跳转到登录页
          app.logout()
        }
      })
    }).catch(function(err) {
      Logger.error('密码修改失败', err)
      var message = (err && err.message) || '密码修改失败，请重试'
      wx.showToast({ title: message, icon: 'none', duration: 2000 })
    }).finally(function() {
      self.setData({ loading: false })
    })
  }
})

