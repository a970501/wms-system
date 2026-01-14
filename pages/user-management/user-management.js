// pages/user-management/user-management.js
var app = getApp()
var Logger = require('../../utils/logger')

Page({
  data: {
    users: [],
    loading: false,
    resetPasswordVisible: false,
    selectedUser: null,
    newPassword: '123456'
  },

  onLoad: function() {
    // 验证管理员权限
    if (!this.checkAdminPermission()) {
      return
    }
  },

  onShow: function() {
    if (!app.checkLogin()) return
    if (!this.checkAdminPermission()) return
    this.loadUsers()
  },

  // 检查管理员权限
  checkAdminPermission: function() {
    var userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    if (userInfo.role !== 'ADMIN') {
      wx.showToast({ title: '无权限访问', icon: 'none' })
      setTimeout(function() {
        wx.switchTab({ url: '/pages/index/index' })
      }, 1500)
      return false
    }
    return true
  },

  // 加载用户列表
  loadUsers: function() {
    var self = this
    this.setData({ loading: true })

    app.request({
      url: '/users',
      method: 'GET'
    }).then(function(res) {
      var users = res.data || []
      Logger.info('加载用户列表成功，共 ' + users.length + ' 个用户')
      self.setData({ users: users })
    }).catch(function(err) {
      Logger.error('加载用户列表失败', err)
      wx.showToast({ title: '加载失败', icon: 'none' })
    }).finally(function() {
      self.setData({ loading: false })
    })
  },

  // 下拉刷新
  onPullDownRefresh: function() {
    this.loadUsers()
    wx.stopPullDownRefresh()
  },

  // 显示重置密码对话框
  showResetPassword: function(e) {
    var user = e.currentTarget.dataset.user
    this.setData({
      resetPasswordVisible: true,
      selectedUser: user,
      newPassword: '123456'
    })
  },

  // 隐藏重置密码对话框
  hideResetPassword: function() {
    this.setData({
      resetPasswordVisible: false,
      selectedUser: null,
      newPassword: '123456'
    })
  },

  // 输入新密码
  onNewPasswordInput: function(e) {
    this.setData({ newPassword: e.detail.value })
  },

  // 确认重置密码
  confirmResetPassword: function() {
    var self = this
    var user = this.data.selectedUser
    var newPassword = this.data.newPassword.trim()

    if (!user) return

    if (!newPassword || newPassword.length < 6) {
      wx.showToast({ title: '密码至少6位', icon: 'none' })
      return
    }

    wx.showLoading({ title: '重置中...' })

    app.request({
      url: '/users/' + user.id + '/reset-password',
      method: 'PUT',
      data: { password: newPassword }
    }).then(function(res) {
      Logger.info('重置用户密码成功: ' + user.username)
      wx.showToast({ title: '密码已重置', icon: 'success' })
      self.hideResetPassword()
    }).catch(function(err) {
      Logger.error('重置密码失败', err)
      var message = (err && err.message) || '重置失败'
      wx.showToast({ title: message, icon: 'none' })
    }).finally(function() {
      wx.hideLoading()
    })
  },

  // 获取角色显示文本
  getRoleText: function(role) {
    var roleMap = {
      'ADMIN': '管理员',
      'USER': '普通用户',
      'operator': '操作员'
    }
    return roleMap[role] || role
  },

  // 获取状态显示文本
  getStatusText: function(status) {
    return status === 'active' ? '正常' : '禁用'
  }
})

