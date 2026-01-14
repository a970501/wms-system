// pages/notification-settings/settings.js
var app = getApp()
var WechatNotification = require('../../utils/wechat-notification')

Page({
  data: {
    notificationStatus: {},
    emailTo: '',
    emailFrom: '',
    mailHost: 'smtp.qq.com',
    mailPort: '587',
    mailUsername: '',
    mailPassword: '',
    wechatWebhook: '',
    wechatWorkCorpId: '',
    wechatWorkAgentId: '',
    wechatWorkSecret: '',
    wechatWorkUserId: '',
    wechatOfficialAppId: '',
    wechatOfficialAppSecret: '',
    wechatOfficialTemplateId: '',
    wechatOfficialOpenId: '',
    wechatMiniAppId: '',
    wechatMiniAppSecret: '',
    wechatMiniTemplateId: '',
    wechatMiniOpenId: '',
    wechatMiniPage: '',
    // 微信通知相关
    wechatNotificationEnabled: false,
    wechatRecipients: [],
    showAddRecipientDialog: false,
    newRecipient: {
      openid: '',
      name: '',
      role: 'user'
    },
    // 邮件接收者相关
    emailRecipients: [],
    showAddEmailRecipientDialog: false,
    showEmailRecipientSettingsDialog: false,
    currentEmailRecipient: null,
    newEmailRecipient: {
      email: '',
      name: '',
      role: 'user',
      messageTypes: {
        system_alert: true,
        daily_report: true,
        inventory_alert: true,
        piecework_report: true,
        user_operation: false
      }
    },
    saving: false,
    testing: false,
    sendingPiecework: false,
    testingWeChatWork: false,
    testingWeChatOfficial: false,
    testingWeChatMini: false,
    currentTab: 'email', // email, wechat, webhook, message-types
    // 消息类型相关
    messageTypes: [
      { type: 'system_alert', name: '系统告警', description: '系统错误、异常等重要信息' },
      { type: 'daily_report', name: '日志报告', description: '每日系统运行状态报告' },
      { type: 'inventory_alert', name: '库存提醒', description: '库存不足、异常变动等提醒' },
      { type: 'piecework_report', name: '计件统计', description: '月度计件工作统计报告' },
      { type: 'user_operation', name: '用户操作', description: '重要用户操作记录通知' }
    ],
    emailMessageTypes: {
      system_alert: true,
      daily_report: true,
      inventory_alert: true,
      piecework_report: true,
      user_operation: false
    },
    wechatMessageTypes: {
      system_alert: true,
      daily_report: true,
      inventory_alert: true,
      piecework_report: true,
      user_operation: false
    },
    savingMessageTypes: false
  },

  onLoad: function() {
    if (!app.checkLogin()) return
    
    // 设置加载状态
    this.setData({ loading: true })
    
    // 优先从后端加载配置，确保所有设备都能看到最新配置
    this.loadConfigFromBackend()
    this.loadStatus()
    this.loadWechatConfig()
    
    // 先从本地加载邮件接收者（快速显示），然后从后端同步
    this.loadEmailRecipientsFromLocal()
    this.loadEmailRecipients()
    
    // 先从本地加载消息类型（快速显示），然后从后端同步
    this.loadMessageTypesFromLocal()
    this.loadMessageTypes()
  },

  onShow: function() {
    // 每次显示页面时，从后端重新加载配置
    this.loadConfigFromBackend()
    this.loadWechatConfig()
    
    // 重新加载邮件接收者和消息类型
    this.loadEmailRecipients()
    this.loadMessageTypes()
  },

  loadStatus: function() {
    var self = this
    app.request({
      url: '/notification/status'
    }).then(function(res) {
      if (res.data) {
        self.setData({ notificationStatus: res.data })
      }
    }).catch(function(err) {
      console.error('获取通知状态失败', err)
    })
  },

  // 从后端加载配置
  loadConfigFromBackend: function() {
    var self = this
    app.request({
      url: '/notification/config',
      method: 'GET'
    }).then(function(res) {
      if (res.data) {
        var config = res.data
        // 从本地存储获取密码（后端不返回密码）
        var localConfig = wx.getStorageSync('notificationConfig') || {}
        
        self.setData({
          emailTo: config.emailTo || '',
          emailFrom: config.emailFrom || '',
          mailHost: config.mailHost || 'smtp.qq.com',
          mailPort: config.mailPort ? String(config.mailPort) : '587',
          mailUsername: config.mailUsername || '',
          mailPassword: localConfig.mailPassword || '', // 密码从本地存储获取
          wechatWebhook: config.wechatWebhook || '',
          wechatWorkCorpId: config.wechatWorkCorpId || '',
          wechatWorkAgentId: config.wechatWorkAgentId || '',
          wechatWorkSecret: localConfig.wechatWorkSecret || '',
          wechatWorkUserId: config.wechatWorkUserId || '',
          wechatOfficialAppId: config.wechatOfficialAppId || '',
          wechatOfficialAppSecret: localConfig.wechatOfficialAppSecret || '',
          wechatOfficialTemplateId: config.wechatOfficialTemplateId || '',
          wechatOfficialOpenId: config.wechatOfficialOpenId || '',
          wechatMiniAppId: config.wechatMiniAppId || '',
          wechatMiniAppSecret: localConfig.wechatMiniAppSecret || '',
          wechatMiniTemplateId: config.wechatMiniTemplateId || '',
          wechatMiniOpenId: config.wechatMiniOpenId || '',
          wechatMiniPage: config.wechatMiniPage || ''
        })
        
        // 同时更新本地存储（除了密码）
        wx.setStorageSync('notificationConfig', {
          emailTo: config.emailTo || '',
          emailFrom: config.emailFrom || '',
          mailHost: config.mailHost || 'smtp.qq.com',
          mailPort: config.mailPort ? String(config.mailPort) : '587',
          mailUsername: config.mailUsername || '',
          mailPassword: localConfig.mailPassword || '', // 保留本地密码
          wechatWebhook: config.wechatWebhook || ''
        })
      }
    }).catch(function(err) {
      console.log('从后端加载配置失败，尝试从本地加载', err)
      // 如果后端加载失败，尝试从本地加载
      self.loadSavedConfig()
    })
  },

  loadSavedConfig: function() {
    // 从本地存储加载已保存的配置（作为后备方案）
    var savedConfig = wx.getStorageSync('notificationConfig') || {}
    if (savedConfig.emailTo || savedConfig.mailUsername) {
      this.setData({
          emailTo: savedConfig.emailTo || '',
          emailFrom: savedConfig.emailFrom || '',
          mailHost: savedConfig.mailHost || 'smtp.qq.com',
          mailPort: savedConfig.mailPort || '587',
          mailUsername: savedConfig.mailUsername || '',
          mailPassword: savedConfig.mailPassword || '',
          wechatWebhook: savedConfig.wechatWebhook || '',
          wechatWorkCorpId: savedConfig.wechatWorkCorpId || '',
          wechatWorkAgentId: savedConfig.wechatWorkAgentId || '',
          wechatWorkSecret: savedConfig.wechatWorkSecret || '',
          wechatWorkUserId: savedConfig.wechatWorkUserId || '',
          wechatOfficialAppId: savedConfig.wechatOfficialAppId || '',
          wechatOfficialAppSecret: savedConfig.wechatOfficialAppSecret || '',
          wechatOfficialTemplateId: savedConfig.wechatOfficialTemplateId || '',
          wechatOfficialOpenId: savedConfig.wechatOfficialOpenId || '',
          wechatMiniAppId: savedConfig.wechatMiniAppId || '',
          wechatMiniAppSecret: savedConfig.wechatMiniAppSecret || '',
          wechatMiniTemplateId: savedConfig.wechatMiniTemplateId || '',
          wechatMiniOpenId: savedConfig.wechatMiniOpenId || '',
          wechatMiniPage: savedConfig.wechatMiniPage || ''
        })
    }
  },

  onEmailToInput: function(e) {
    this.setData({ emailTo: e.detail.value })
  },

  onEmailFromInput: function(e) {
    this.setData({ emailFrom: e.detail.value })
  },

  onMailHostInput: function(e) {
    this.setData({ mailHost: e.detail.value })
  },

  onMailPortInput: function(e) {
    this.setData({ mailPort: e.detail.value })
  },

  onMailUsernameInput: function(e) {
    this.setData({ mailUsername: e.detail.value })
  },

  onMailPasswordInput: function(e) {
    this.setData({ mailPassword: e.detail.value })
  },

  onWechatWebhookInput: function(e) {
    this.setData({ wechatWebhook: e.detail.value })
  },

  onWechatWorkCorpIdInput: function(e) {
    this.setData({ wechatWorkCorpId: e.detail.value })
  },

  onWechatWorkAgentIdInput: function(e) {
    this.setData({ wechatWorkAgentId: e.detail.value })
  },

  onWechatWorkSecretInput: function(e) {
    this.setData({ wechatWorkSecret: e.detail.value })
  },

  onWechatWorkUserIdInput: function(e) {
    this.setData({ wechatWorkUserId: e.detail.value })
  },

  onWechatOfficialAppIdInput: function(e) {
    this.setData({ wechatOfficialAppId: e.detail.value })
  },

  onWechatOfficialAppSecretInput: function(e) {
    this.setData({ wechatOfficialAppSecret: e.detail.value })
  },

  onWechatOfficialTemplateIdInput: function(e) {
    this.setData({ wechatOfficialTemplateId: e.detail.value })
  },

  onWechatOfficialOpenIdInput: function(e) {
    this.setData({ wechatOfficialOpenId: e.detail.value })
  },

  onWechatMiniAppIdInput: function(e) {
    this.setData({ wechatMiniAppId: e.detail.value })
  },

  onWechatMiniAppSecretInput: function(e) {
    this.setData({ wechatMiniAppSecret: e.detail.value })
  },

  onWechatMiniTemplateIdInput: function(e) {
    this.setData({ wechatMiniTemplateId: e.detail.value })
  },

  onWechatMiniOpenIdInput: function(e) {
    this.setData({ wechatMiniOpenId: e.detail.value })
  },

  onWechatMiniPageInput: function(e) {
    this.setData({ wechatMiniPage: e.detail.value })
  },

  // 微信通知相关方法
  loadWechatConfig: function() {
    var config = WechatNotification.getConfig()
    var recipients = WechatNotification.getRecipients()
    
    this.setData({
      wechatNotificationEnabled: config.enabled,
      wechatRecipients: recipients
    })
  },

  switchTab: function(e) {
    var tab = e.currentTarget.dataset.tab
    this.setData({ currentTab: tab })
  },

  toggleWechatNotification: function(e) {
    var enabled = e.detail.value
    WechatNotification.setEnabled(enabled)
    this.setData({ wechatNotificationEnabled: enabled })
    
    wx.showToast({ 
      title: enabled ? '微信通知已启用' : '微信通知已禁用', 
      icon: 'success' 
    })
  },

  showAddRecipientDialog: function() {
    this.setData({ 
      showAddRecipientDialog: true,
      newRecipient: { openid: '', name: '', role: 'user' }
    })
    // 延迟一下，确保对话框完全显示后再处理焦点
    setTimeout(function() {
      // 可以在这里添加额外的初始化逻辑
    }, 100)
  },

  hideAddRecipientDialog: function() {
    this.setData({ showAddRecipientDialog: false })
  },

  onRecipientInput: function(e) {
    var field = e.currentTarget.dataset.field
    var value = e.detail.value
    var updateData = {}
    updateData['newRecipient.' + field] = value
    this.setData(updateData)
  },

  onRecipientRoleChange: function(e) {
    var roles = ['user', 'admin']
    var index = e.detail.value
    this.setData({ 'newRecipient.role': roles[index] })
  },

  addRecipient: function() {
    var recipient = this.data.newRecipient
    
    if (!recipient.openid.trim()) {
      wx.showToast({ title: '请输入OpenID', icon: 'none' })
      return
    }
    
    if (!recipient.name.trim()) {
      wx.showToast({ title: '请输入姓名', icon: 'none' })
      return
    }

    try {
      WechatNotification.addRecipient({
        openid: recipient.openid.trim(),
        name: recipient.name.trim(),
        role: recipient.role
      })
      
      this.loadWechatConfig()
      this.hideAddRecipientDialog()
      wx.showToast({ title: '添加成功', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' })
    }
  },

  removeRecipient: function(e) {
    var self = this
    var openid = e.currentTarget.dataset.openid
    var name = e.currentTarget.dataset.name
    
    wx.showModal({
      title: '确认删除',
      content: '确定要删除接收者 "' + name + '" 吗？',
      success: function(res) {
        if (res.confirm) {
          WechatNotification.removeRecipient(openid)
          self.loadWechatConfig()
          wx.showToast({ title: '删除成功', icon: 'success' })
        }
      }
    })
  },

  testWechatNotification: function() {
    var self = this
    this.setData({ testing: true })
    
    WechatNotification.testNotification().then(function(result) {
      if (result.success) {
        wx.showModal({
          title: '测试结果',
          content: '微信通知测试：' + result.message,
          showCancel: false
        })
      } else {
        wx.showModal({
          title: '测试失败',
          content: result.message || '发送失败，请检查配置',
          showCancel: false
        })
      }
    }).catch(function(error) {
      wx.showModal({
        title: '测试失败',
        content: error.message || '网络错误',
        showCancel: false
      })
    }).finally(function() {
      self.setData({ testing: false })
    })
  },

  sendDailyReportToWechat: function() {
    var self = this
    this.setData({ sendingPiecework: true })
    
    // 获取今日统计数据
    var today = new Date().toISOString().split('T')[0]
    
    app.request({
      url: '/piecework?startDate=' + today + '&endDate=' + today
    }).then(function(res) {
      var records = res.data || []
      var totalCount = 0
      var totalAmount = 0
      
      records.forEach(function(record) {
        totalCount += parseInt(record.quantity) || 0
        totalAmount += parseFloat(record.totalAmount) || 0
      })
      
      return WechatNotification.sendDailyReport({
        date: new Date().toLocaleDateString('zh-CN'),
        pieceworkCount: totalCount,
        totalAmount: totalAmount.toFixed(2),
        inventoryChanges: 0 // 这里可以添加库存变动统计
      })
    }).then(function(result) {
      if (result.success) {
        wx.showModal({
          title: '发送成功',
          content: '每日报告已发送：' + result.message,
          showCancel: false
        })
      } else {
        wx.showModal({
          title: '发送失败',
          content: result.message,
          showCancel: false
        })
      }
    }).catch(function(error) {
      wx.showModal({
        title: '发送失败',
        content: error.message || '获取数据失败',
        showCancel: false
      })
    }).finally(function() {
      self.setData({ sendingPiecework: false })
    })
  },

  saveConfig: function() {
    var self = this
    var data = this.data
    var emailTo = data.emailTo
    var emailFrom = data.emailFrom
    var mailHost = data.mailHost
    var mailPort = data.mailPort
    var mailUsername = data.mailUsername
    var mailPassword = data.mailPassword
    var wechatWebhook = data.wechatWebhook
    var wechatWorkCorpId = data.wechatWorkCorpId
    var wechatWorkAgentId = data.wechatWorkAgentId
    var wechatWorkSecret = data.wechatWorkSecret
    var wechatWorkUserId = data.wechatWorkUserId
    var wechatOfficialAppId = data.wechatOfficialAppId
    var wechatOfficialAppSecret = data.wechatOfficialAppSecret
    var wechatOfficialTemplateId = data.wechatOfficialTemplateId
    var wechatOfficialOpenId = data.wechatOfficialOpenId
    var wechatMiniAppId = data.wechatMiniAppId
    var wechatMiniAppSecret = data.wechatMiniAppSecret
    var wechatMiniTemplateId = data.wechatMiniTemplateId
    var wechatMiniOpenId = data.wechatMiniOpenId
    var wechatMiniPage = data.wechatMiniPage

    var hasEmail = emailTo || emailFrom || mailUsername
    var hasWechatWebhook = wechatWebhook
    var hasWechatWork = wechatWorkCorpId && wechatWorkAgentId && wechatWorkSecret && wechatWorkUserId
    var hasWechatOfficial = wechatOfficialAppId && wechatOfficialAppSecret && wechatOfficialTemplateId && wechatOfficialOpenId
    var hasWechatMini = wechatMiniAppId && wechatMiniAppSecret && wechatMiniTemplateId && wechatMiniOpenId

    if (!hasEmail && !hasWechatWebhook && !hasWechatWork && !hasWechatOfficial && !hasWechatMini) {
      wx.showToast({ title: '请至少配置一种通知方式', icon: 'none' })
      return
    }

    if (hasEmail && (!emailFrom || !mailUsername || !mailPassword)) {
      wx.showToast({ title: '邮件配置不完整', icon: 'none' })
      return
    }

    this.setData({ saving: true })

    var configData = {
      emailTo: emailTo,
      emailFrom: emailFrom,
      mailHost: mailHost,
      mailPort: parseInt(mailPort) || 587,
      mailUsername: mailUsername,
      mailPassword: mailPassword,
      wechatWebhook: wechatWebhook,
      wechatWorkCorpId: wechatWorkCorpId,
      wechatWorkAgentId: wechatWorkAgentId,
      wechatWorkSecret: wechatWorkSecret,
      wechatWorkUserId: wechatWorkUserId,
      wechatOfficialAppId: wechatOfficialAppId,
      wechatOfficialAppSecret: wechatOfficialAppSecret,
      wechatOfficialTemplateId: wechatOfficialTemplateId,
      wechatOfficialOpenId: wechatOfficialOpenId,
      wechatMiniAppId: wechatMiniAppId,
      wechatMiniAppSecret: wechatMiniAppSecret,
      wechatMiniTemplateId: wechatMiniTemplateId,
      wechatMiniOpenId: wechatMiniOpenId,
      wechatMiniPage: wechatMiniPage,
      enabled: true
    }

    app.request({
      url: '/notification/config',
      method: 'POST',
      data: configData
    }).then(function(res) {
      var localConfig = {
        emailTo: emailTo,
        emailFrom: emailFrom,
        mailHost: mailHost,
        mailPort: mailPort,
        mailUsername: mailUsername,
        mailPassword: mailPassword,
        wechatWebhook: wechatWebhook,
        wechatWorkCorpId: wechatWorkCorpId,
        wechatWorkAgentId: wechatWorkAgentId,
        wechatWorkSecret: wechatWorkSecret,
        wechatWorkUserId: wechatWorkUserId,
        wechatOfficialAppId: wechatOfficialAppId,
        wechatOfficialAppSecret: wechatOfficialAppSecret,
        wechatOfficialTemplateId: wechatOfficialTemplateId,
        wechatOfficialOpenId: wechatOfficialOpenId,
        wechatMiniAppId: wechatMiniAppId,
        wechatMiniAppSecret: wechatMiniAppSecret,
        wechatMiniTemplateId: wechatMiniTemplateId,
        wechatMiniOpenId: wechatMiniOpenId,
        wechatMiniPage: wechatMiniPage
      }

      wx.setStorageSync('notificationConfig', localConfig)

      wx.showToast({ title: '配置已保存', icon: 'success' })
      self.loadStatus()
    }).catch(function(err) {
      console.error('保存配置失败', err)
      var localConfig = {
        emailTo: emailTo,
        emailFrom: emailFrom,
        mailHost: mailHost,
        mailPort: mailPort,
        mailUsername: mailUsername,
        mailPassword: mailPassword,
        wechatWebhook: wechatWebhook,
        wechatWorkCorpId: wechatWorkCorpId,
        wechatWorkAgentId: wechatWorkAgentId,
        wechatWorkSecret: wechatWorkSecret,
        wechatWorkUserId: wechatWorkUserId,
        wechatOfficialAppId: wechatOfficialAppId,
        wechatOfficialAppSecret: wechatOfficialAppSecret,
        wechatOfficialTemplateId: wechatOfficialTemplateId,
        wechatOfficialOpenId: wechatOfficialOpenId,
        wechatMiniAppId: wechatMiniAppId,
        wechatMiniAppSecret: wechatMiniAppSecret,
        wechatMiniTemplateId: wechatMiniTemplateId,
        wechatMiniOpenId: wechatMiniOpenId,
        wechatMiniPage: wechatMiniPage
      }
      wx.setStorageSync('notificationConfig', localConfig)
      wx.showModal({
        title: '提示',
        content: '配置已保存到本地。\n\n注意：需要在服务器配置环境变量才能生效，详见维护手册。',
        showCancel: false
      })
    }).finally(function() {
      self.setData({ saving: false })
    })
  },

  sendTestReport: function() {
    var self = this
    this.setData({ testing: true })

    app.request({
      url: '/notification/send-report',
      method: 'POST'
    }).then(function(res) {
      if (res.data && res.data.success) {
        var channels = res.data.channels || []
        wx.showModal({
          title: '发送成功',
          content: '日志报告已发送到：\n' + channels.join('\n'),
          showCancel: false
        })
      } else {
        wx.showModal({
          title: '发送失败',
          content: (res.data && res.data.message) || '未知错误',
          showCancel: false
        })
      }
    }).catch(function(err) {
      console.error('发送测试失败', err)
      wx.showModal({
        title: '发送失败',
        content: err.message || '请检查通知配置是否正确',
        showCancel: false
      })
    }).finally(function() {
      self.setData({ testing: false })
    })
  },

  sendPieceworkReport: function() {
    var self = this
    this.setData({ sendingPiecework: true })

    app.request({
      url: '/notification/send-piecework-report',
      method: 'POST'
    }).then(function(res) {
      if (res.data && res.data.success) {
        wx.showModal({
          title: '发送成功',
          content: '月度计件统计报告已发送',
          showCancel: false
        })
      } else {
        wx.showModal({
          title: '发送失败',
          content: (res.data && res.data.message) || '未知错误',
          showCancel: false
        })
      }
    }).catch(function(err) {
      console.error('发送计件报告失败', err)
      wx.showModal({
        title: '发送失败',
        content: err.message || '请检查邮件配置是否正确',
        showCancel: false
      })
    }).finally(function() {
      self.setData({ sendingPiecework: false })
    })
  },

  testWeChatWork: function() {
    var self = this
    var userId = this.data.wechatWorkUserId

    if (!userId) {
      wx.showToast({ title: '请先配置接收人用户ID', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认发送',
      content: '确定要发送测试消息到企业微信应用吗？',
      success: function(res) {
        if (res.confirm) {
          self.setData({ testingWeChatWork: true })

          app.request({
            url: '/notification/send-wechat-work',
            method: 'POST',
            data: { userId: userId }
          }).then(function(res) {
            if (res.data && res.data.success) {
              wx.showToast({ title: '发送成功', icon: 'success' })
            } else {
              wx.showModal({
                title: '发送失败',
                content: (res.data && res.data.message) || '未知错误',
                showCancel: false
              })
            }
          }).catch(function(err) {
            console.error('发送企业微信应用消息失败', err)
            wx.showModal({
              title: '发送失败',
              content: err.message || '请检查配置是否正确',
              showCancel: false
            })
          }).finally(function() {
            self.setData({ testingWeChatWork: false })
          })
        }
      }
    })
  },

  testWeChatOfficial: function() {
    var self = this
    var openId = this.data.wechatOfficialOpenId

    if (!openId) {
      wx.showToast({ title: '请先配置接收人OpenID', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认发送',
      content: '确定要发送测试消息到微信公众号吗？',
      success: function(res) {
        if (res.confirm) {
          self.setData({ testingWeChatOfficial: true })

          app.request({
            url: '/notification/send-wechat-official',
            method: 'POST',
            data: { 
              openId: openId,
              url: 'https://example.com'
            }
          }).then(function(res) {
            if (res.data && res.data.success) {
              wx.showToast({ title: '发送成功', icon: 'success' })
            } else {
              wx.showModal({
                title: '发送失败',
                content: (res.data && res.data.message) || '未知错误',
                showCancel: false
              })
            }
          }).catch(function(err) {
            console.error('发送微信公众号消息失败', err)
            wx.showModal({
              title: '发送失败',
              content: err.message || '请检查配置是否正确',
              showCancel: false
            })
          }).finally(function() {
            self.setData({ testingWeChatOfficial: false })
          })
        }
      }
    })
  },

  testWeChatMini: function() {
    var self = this
    var openId = this.data.wechatMiniOpenId

    if (!openId) {
      wx.showToast({ title: '请先配置接收人OpenID', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认发送',
      content: '确定要发送测试消息到微信小程序吗？',
      success: function(res) {
        if (res.confirm) {
          self.setData({ testingWeChatMini: true })

          app.request({
            url: '/notification/send-wechat-mini',
            method: 'POST',
            data: { 
              openId: openId,
              page: self.data.wechatMiniPage || 'pages/index/index'
            }
          }).then(function(res) {
            if (res.data && res.data.success) {
              wx.showToast({ title: '发送成功', icon: 'success' })
            } else {
              wx.showModal({
                title: '发送失败',
                content: (res.data && res.data.message) || '未知错误',
                showCancel: false
              })
            }
          }).catch(function(err) {
            console.error('发送微信小程序消息失败', err)
            wx.showModal({
              title: '发送失败',
              content: err.message || '请检查配置是否正确',
              showCancel: false
            })
          }).finally(function() {
            self.setData({ testingWeChatMini: false })
          })
        }
      }
    })
  },

  // 邮件接收者管理方法
  showAddEmailRecipientDialog: function() {
    this.setData({ 
      showAddEmailRecipientDialog: true,
      newEmailRecipient: { 
        email: '', 
        name: '', 
        role: 'user',
        messageTypes: {
          system_alert: true,
          daily_report: true,
          inventory_alert: true,
          piecework_report: true,
          user_operation: false
        }
      }
    })
  },

  hideAddEmailRecipientDialog: function() {
    this.setData({ showAddEmailRecipientDialog: false })
  },

  onEmailRecipientInput: function(e) {
    var field = e.currentTarget.dataset.field
    var value = e.detail.value
    var updateData = {}
    updateData['newEmailRecipient.' + field] = value
    this.setData(updateData)
  },

  onEmailRecipientRoleChange: function(e) {
    var roles = ['user', 'admin']
    var index = e.detail.value
    this.setData({ 'newEmailRecipient.role': roles[index] })
  },

  addEmailRecipient: function() {
    var recipient = this.data.newEmailRecipient
    
    // 验证邮箱格式
    var emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!recipient.email.trim()) {
      wx.showToast({ title: '请输入邮箱地址', icon: 'none' })
      return
    }
    
    if (!emailRegex.test(recipient.email.trim())) {
      wx.showToast({ title: '请输入有效的邮箱地址', icon: 'none' })
      return
    }
    
    if (!recipient.name.trim()) {
      wx.showToast({ title: '请输入姓名', icon: 'none' })
      return
    }

    // 检查邮箱是否已存在
    var existingRecipients = this.data.emailRecipients || []
    var exists = existingRecipients.some(function(item) {
      return item.email === recipient.email.trim()
    })
    
    if (exists) {
      wx.showToast({ title: '该邮箱已存在', icon: 'none' })
      return
    }

    // 添加到列表
    var newRecipients = existingRecipients.concat([{
      email: recipient.email.trim(),
      name: recipient.name.trim(),
      role: recipient.role,
      messageTypes: recipient.messageTypes
    }])
    
    this.setData({ emailRecipients: newRecipients })
    this.saveEmailRecipients(newRecipients)
    this.hideAddEmailRecipientDialog()
    wx.showToast({ title: '添加成功', icon: 'success' })
  },

  removeEmailRecipient: function(e) {
    var email = e.currentTarget.dataset.email
    var name = e.currentTarget.dataset.name
    var self = this
    
    wx.showModal({
      title: '确认删除',
      content: '确定要删除邮件接收者 "' + name + '" 吗？',
      success: function(res) {
        if (res.confirm) {
          var newRecipients = self.data.emailRecipients.filter(function(item) {
            return item.email !== email
          })
          self.setData({ emailRecipients: newRecipients })
          self.saveEmailRecipients(newRecipients)
          wx.showToast({ title: '删除成功', icon: 'success' })
        }
      }
    })
  },

  saveEmailRecipients: function(recipients) {
    var self = this
    try {
      // 保存到本地存储
      wx.setStorageSync('emailRecipients', recipients)
      console.log('邮件接收者已保存到本地存储:', recipients)
      
      // 同时保存到后端
      app.request({
        url: '/notification/email-recipients',
        method: 'POST',
        data: { recipients: recipients }
      }).then(function(res) {
        if (res.data && res.data.success) {
          console.log('邮件接收者列表已同步到后端')
        } else {
          console.warn('同步邮件接收者到后端失败:', res)
        }
      }).catch(function(err) {
        console.error('同步邮件接收者到后端失败', err)
      })
      
      // 同时更新emailTo字段为第一个邮箱（保持向后兼容）
      if (recipients.length > 0) {
        var emailList = recipients.map(function(item) { return item.email }).join(',')
        this.setData({ emailTo: emailList })
      } else {
        this.setData({ emailTo: '' })
      }
    } catch (error) {
      console.error('保存邮件接收者失败', error)
      wx.showToast({ title: '保存失败，请重试', icon: 'none' })
    }
  },

  loadEmailRecipients: function() {
    var self = this
    
    // 优先从后端加载
    app.request({
      url: '/notification/email-recipients',
      method: 'GET'
    }).then(function(res) {
      if (res.data && res.data.success) {
        var recipients = res.data.recipients || []
        
        // 确保每个接收者都有消息类型设置
        recipients = recipients.map(function(recipient) {
          if (!recipient.messageTypes) {
            recipient.messageTypes = {
              system_alert: true,
              daily_report: true,
              inventory_alert: true,
              piecework_report: true,
              user_operation: false
            }
          }
          return recipient
        })
        
        console.log('从后端加载邮件接收者成功:', recipients)
        self.setData({ emailRecipients: recipients })
        
        // 同步到本地存储
        try {
          wx.setStorageSync('emailRecipients', recipients)
          console.log('邮件接收者已同步到本地存储')
        } catch (error) {
          console.error('同步邮件接收者到本地失败', error)
        }
      } else {
        console.warn('后端返回邮件接收者数据格式异常:', res)
      }
    }).catch(function(err) {
      console.error('从后端加载邮件接收者失败，使用本地数据', err)
    })
  },

  loadEmailRecipientsFromLocal: function() {
    var self = this
    
    try {
      var recipients = wx.getStorageSync('emailRecipients') || []
      
      // 确保每个接收者都有消息类型设置
      recipients = recipients.map(function(recipient) {
        if (!recipient.messageTypes) {
          recipient.messageTypes = {
            system_alert: true,
            daily_report: true,
            inventory_alert: true,
            piecework_report: true,
            user_operation: false
          }
        }
        return recipient
      })
      
      console.log('从本地存储加载邮件接收者:', recipients)
      self.setData({ emailRecipients: recipients })
      
      // 如果没有邮件接收者但有emailTo，自动创建一个接收者
      if (recipients.length === 0 && self.data.emailTo) {
        var emails = self.data.emailTo.split(',')
        var defaultRecipients = []
        
        for (var i = 0; i < emails.length; i++) {
          var email = emails[i].trim()
          if (email) {
            defaultRecipients.push({
              email: email,
              name: '接收者' + (i + 1),
              role: 'admin',
              messageTypes: {
                system_alert: true,
                daily_report: true,
                inventory_alert: true,
                piecework_report: true,
                user_operation: false
              }
            })
          }
        }
        
        if (defaultRecipients.length > 0) {
          console.log('自动创建默认邮件接收者:', defaultRecipients)
          self.setData({ emailRecipients: defaultRecipients })
          self.saveEmailRecipients(defaultRecipients)
        }
      }
    } catch (error) {
      console.error('从本地存储加载邮件接收者失败', error)
      // 如果本地存储也失败，设置空数组
      self.setData({ emailRecipients: [] })
    }
  },

  // 阻止事件冒泡，防止对话框意外关闭
  stopPropagation: function(e) {
    // 阻止事件冒泡
    return false
  },

  // 消息类型管理方法
  loadMessageTypesFromLocal: function() {
    var self = this
    
    try {
      var savedTypes = wx.getStorageSync('messageTypes') || {}
      if (savedTypes.emailMessageTypes || savedTypes.wechatMessageTypes) {
        console.log('从本地存储加载消息类型:', savedTypes)
        self.setData({
          emailMessageTypes: savedTypes.emailMessageTypes || self.data.emailMessageTypes,
          wechatMessageTypes: savedTypes.wechatMessageTypes || self.data.wechatMessageTypes
        })
      }
    } catch (error) {
      console.error('从本地存储加载消息类型失败', error)
    }
  },

  loadMessageTypes: function() {
    var self = this
    
    // 优先从后端加载
    app.request({
      url: '/notification/message-types',
      method: 'GET'
    }).then(function(res) {
      if (res.data && res.data.success) {
        var emailTypes = res.data.emailMessageTypes || {}
        var wechatTypes = res.data.wechatMessageTypes || {}
        
        console.log('从后端加载消息类型成功:', { emailTypes: emailTypes, wechatTypes: wechatTypes })
        
        self.setData({
          emailMessageTypes: emailTypes,
          wechatMessageTypes: wechatTypes
        })
        
        // 同步到本地存储
        try {
          wx.setStorageSync('messageTypes', {
            emailMessageTypes: emailTypes,
            wechatMessageTypes: wechatTypes
          })
          console.log('消息类型已同步到本地存储')
        } catch (error) {
          console.error('同步消息类型到本地失败', error)
        }
      } else {
        console.warn('后端返回消息类型数据格式异常:', res)
      }
    }).catch(function(err) {
      console.error('从后端加载消息类型失败，使用本地数据', err)
    })
  },

  toggleEmailMessageType: function(e) {
    var type = e.currentTarget.dataset.type
    var currentTypes = this.data.emailMessageTypes
    var newTypes = {}
    
    // 复制当前设置
    for (var key in currentTypes) {
      newTypes[key] = currentTypes[key]
    }
    
    // 切换选中状态
    newTypes[type] = !newTypes[type]
    
    this.setData({ emailMessageTypes: newTypes })
  },

  toggleWechatMessageType: function(e) {
    var type = e.currentTarget.dataset.type
    var currentTypes = this.data.wechatMessageTypes
    var newTypes = {}
    
    // 复制当前设置
    for (var key in currentTypes) {
      newTypes[key] = currentTypes[key]
    }
    
    // 切换选中状态
    newTypes[type] = !newTypes[type]
    
    this.setData({ wechatMessageTypes: newTypes })
  },

  selectAllEmailTypes: function() {
    var newTypes = {}
    var messageTypes = this.data.messageTypes
    
    for (var i = 0; i < messageTypes.length; i++) {
      newTypes[messageTypes[i].type] = true
    }
    
    this.setData({ emailMessageTypes: newTypes })
  },

  clearAllEmailTypes: function() {
    var newTypes = {}
    var messageTypes = this.data.messageTypes
    
    for (var i = 0; i < messageTypes.length; i++) {
      newTypes[messageTypes[i].type] = false
    }
    
    this.setData({ emailMessageTypes: newTypes })
  },

  selectAllWechatTypes: function() {
    var newTypes = {}
    var messageTypes = this.data.messageTypes
    
    for (var i = 0; i < messageTypes.length; i++) {
      newTypes[messageTypes[i].type] = true
    }
    
    this.setData({ wechatMessageTypes: newTypes })
  },

  clearAllWechatTypes: function() {
    var newTypes = {}
    var messageTypes = this.data.messageTypes
    
    for (var i = 0; i < messageTypes.length; i++) {
      newTypes[messageTypes[i].type] = false
    }
    
    this.setData({ wechatMessageTypes: newTypes })
  },

  saveMessageTypes: function() {
    var self = this
    this.setData({ savingMessageTypes: true })
    
    var data = {
      emailMessageTypes: this.data.emailMessageTypes,
      wechatMessageTypes: this.data.wechatMessageTypes
    }
    
    app.request({
      url: '/notification/message-types',
      method: 'POST',
      data: data
    }).then(function(res) {
      if (res.data && res.data.success) {
        // 保存到本地存储
        try {
          wx.setStorageSync('messageTypes', data)
        } catch (error) {
          console.error('保存消息类型到本地失败', error)
        }
        
        wx.showToast({ title: '消息类型设置已保存', icon: 'success' })
      } else {
        wx.showModal({
          title: '保存失败',
          content: (res.data && res.data.message) || '保存消息类型设置失败',
          showCancel: false
        })
      }
    }).catch(function(err) {
      console.error('保存消息类型失败', err)
      
      // 即使后端保存失败，也保存到本地
      try {
        wx.setStorageSync('messageTypes', data)
        wx.showModal({
          title: '提示',
          content: '消息类型设置已保存到本地。\n\n注意：需要在服务器同步配置才能完全生效。',
          showCancel: false
        })
      } catch (error) {
        wx.showModal({
          title: '保存失败',
          content: '保存消息类型设置失败，请重试',
          showCancel: false
        })
      }
    }).finally(function() {
      self.setData({ savingMessageTypes: false })
    })
  },

  // 邮件接收者消息类型管理方法
  showEmailRecipientSettings: function(e) {
    var email = e.currentTarget.dataset.email
    var recipients = this.data.emailRecipients
    var recipient = recipients.find(function(item) {
      return item.email === email
    })
    
    if (recipient) {
      // 确保消息类型设置存在
      if (!recipient.messageTypes) {
        recipient.messageTypes = {
          system_alert: true,
          daily_report: true,
          inventory_alert: true,
          piecework_report: true,
          user_operation: false
        }
      }
      
      this.setData({
        showEmailRecipientSettingsDialog: true,
        currentEmailRecipient: JSON.parse(JSON.stringify(recipient)) // 深拷贝
      })
    }
  },

  hideEmailRecipientSettingsDialog: function() {
    this.setData({ 
      showEmailRecipientSettingsDialog: false,
      currentEmailRecipient: null
    })
  },

  toggleNewEmailRecipientMessageType: function(e) {
    var type = e.currentTarget.dataset.type
    var currentTypes = this.data.newEmailRecipient.messageTypes
    var newTypes = {}
    
    // 复制当前设置
    for (var key in currentTypes) {
      newTypes[key] = currentTypes[key]
    }
    
    // 切换选中状态
    newTypes[type] = !newTypes[type]
    
    this.setData({ 'newEmailRecipient.messageTypes': newTypes })
  },

  toggleCurrentEmailRecipientMessageType: function(e) {
    var type = e.currentTarget.dataset.type
    var currentTypes = this.data.currentEmailRecipient.messageTypes
    var newTypes = {}
    
    // 复制当前设置
    for (var key in currentTypes) {
      newTypes[key] = currentTypes[key]
    }
    
    // 切换选中状态
    newTypes[type] = !newTypes[type]
    
    this.setData({ 'currentEmailRecipient.messageTypes': newTypes })
  },

  selectAllCurrentEmailRecipientTypes: function() {
    var newTypes = {}
    var messageTypes = this.data.messageTypes
    
    for (var i = 0; i < messageTypes.length; i++) {
      newTypes[messageTypes[i].type] = true
    }
    
    this.setData({ 'currentEmailRecipient.messageTypes': newTypes })
  },

  clearAllCurrentEmailRecipientTypes: function() {
    var newTypes = {}
    var messageTypes = this.data.messageTypes
    
    for (var i = 0; i < messageTypes.length; i++) {
      newTypes[messageTypes[i].type] = false
    }
    
    this.setData({ 'currentEmailRecipient.messageTypes': newTypes })
  },

  saveEmailRecipientSettings: function() {
    var self = this
    var currentRecipient = this.data.currentEmailRecipient
    var recipients = this.data.emailRecipients
    
    // 更新接收者列表中的消息类型设置
    var updatedRecipients = recipients.map(function(item) {
      if (item.email === currentRecipient.email) {
        return {
          email: item.email,
          name: item.name,
          role: item.role,
          messageTypes: currentRecipient.messageTypes
        }
      }
      return item
    })
    
    this.setData({ emailRecipients: updatedRecipients })
    this.saveEmailRecipients(updatedRecipients)
    this.hideEmailRecipientSettingsDialog()
    wx.showToast({ title: '消息类型设置已保存', icon: 'success' })
  }
})
