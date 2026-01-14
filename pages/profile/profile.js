// pages/profile/profile.js
var app = getApp()

Page({
  data: {
    userInfo: {},
    monthPiecework: 0,
    monthAmount: '0.00'
  },

  onShow() {
    if (!app.checkLogin()) return
    this.setData({
      userInfo: app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    })
    this.loadMonthStats()
  },

  loadMonthStats() {
    // 先重置为0
    this.setData({
      monthPiecework: 0,
      monthAmount: '0.00'
    })

    const now = new Date()
    const year = now.getFullYear()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    const firstDay = `${year}-${month}-01`
    const lastDay = `${year}-${month}-${day}`

    app.request({
      url: `/piecework?startDate=${firstDay}&endDate=${lastDay}`
    }).then(res => {
      // 提取记录数组
      let records = []
      if (Array.isArray(res.data)) {
        records = res.data
      } else if (res.data && Array.isArray(res.data.content)) {
        records = res.data.content
      }

      // 计算总量
      let totalQty = 0
      let totalAmtCents = 0

      for (let i = 0; i < records.length; i++) {
        const item = records[i]
        totalQty += parseInt(item.quantity) || 0
        totalAmtCents += Math.round((parseFloat(item.totalAmount) || 0) * 100)
      }

      // 更新显示
      this.setData({
        monthPiecework: totalQty,
        monthAmount: (totalAmtCents / 100).toFixed(2)
      })
    }).catch(err => {
      console.error('加载失败', err)
    })
  },

  goToMyRecords() {
    wx.switchTab({ url: '/pages/piecework/piecework' })
  },

  goToAdvancedSearch() {
    wx.navigateTo({ url: '/pages/piecework-search/search' })
  },

  goToStorageRules() {
    wx.navigateTo({ url: '/pages/rules/rules?type=storage' })
  },

  goToAssemblyRules() {
    wx.navigateTo({ url: '/pages/rules/rules?type=assembly' })
  },

  goToReconcile() {
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    if (userInfo.role !== 'ADMIN') {
      wx.showToast({ title: '仅管理员可对账', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/reconcile/reconcile' })
  },

  goToStorageLogs() {
    wx.navigateTo({ url: '/pages/storage-logs/logs' })
  },

  goToAuditLogs() {
    wx.navigateTo({ url: '/pages/audit-logs/logs' })
  },

  goToNotificationSettings() {
    wx.navigateTo({ url: '/pages/notification-settings/settings' })
  },

  goToDeveloperTools() {
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    if (userInfo.role !== 'ADMIN') {
      wx.showToast({ title: '仅管理员可访问', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/developer-tools/developer-tools' })
  },

  goToPriceTable() {
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    if (userInfo.role !== 'ADMIN') {
      wx.showToast({ title: '仅管理员可访问', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/price-table/price-table' })
  },

  goToChangePassword() {
    wx.navigateTo({ url: '/pages/change-password/change-password' })
  },

  goToUserManagement() {
    wx.navigateTo({ url: '/pages/user-management/user-management' })
  },

  goToWebsite() {
    wx.setClipboardData({
      data: 'https://cl.bxyxr.com',
      success: () => {
        wx.showToast({ title: '网址已复制', icon: 'success' })
      }
    })
  },

  clearCache() {
    wx.showModal({
      title: '提示',
      content: '确定要清除缓存吗？',
      success: (res) => {
        if (res.confirm) {
          wx.clearStorageSync()
          // 保留登录信息
          if (app.globalData.token) {
            wx.setStorageSync('token', app.globalData.token)
            wx.setStorageSync('userInfo', app.globalData.userInfo)
          }
          wx.showToast({ title: '缓存已清除', icon: 'success' })
        }
      }
    })
  },

  showAbout() {
    wx.showModal({
      title: '关于',
      content: '小马的资料存储 v7.0.1',
      showCancel: false
    })
  },

  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout()
        }
      }
    })
  }
})

