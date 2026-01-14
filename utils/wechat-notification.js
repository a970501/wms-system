// utils/wechat-notification.js - 微信通知管理器
var Logger = require('./logger')

function WechatNotificationManager() {
  this.config = {
    enabled: false,
    recipients: [], // 接收者列表 [{openid: '', name: '', role: ''}]
    templates: {
      log_alert: {
        title: '系统日志告警',
        template: '【仓库管理系统】\n时间：{{time}}\n级别：{{level}}\n内容：{{message}}\n\n请及时处理！'
      },
      daily_report: {
        title: '每日统计报告',
        template: '【每日统计】{{date}}\n\n📊 今日计件：{{piecework_count}}件\n💰 今日金额：¥{{total_amount}}\n📦 库存变动：{{inventory_changes}}项\n\n详情请查看小程序'
      },
      system_alert: {
        title: '系统异常告警',
        template: '【系统告警】\n时间：{{time}}\n类型：{{type}}\n描述：{{description}}\n\n请立即检查系统状态！'
      },
      inventory_alert: {
        title: '库存告警',
        template: '【库存告警】\n时间：{{time}}\n产品：{{product_name}}\n当前库存：{{current_stock}}\n告警阈值：{{threshold}}\n\n请及时补货！'
      }
    }
  }
  this.loadConfig()
}

/**
 * 加载配置
 */
WechatNotificationManager.prototype.loadConfig = function() {
  try {
    var savedConfig = wx.getStorageSync('wechatNotificationConfig')
    if (savedConfig) {
      this.config = Object.assign({}, this.config, savedConfig)
    }
  } catch (error) {
    Logger.error('Failed to load wechat notification config', error)
  }
}

/**
 * 保存配置
 */
WechatNotificationManager.prototype.saveConfig = function() {
  try {
    wx.setStorageSync('wechatNotificationConfig', this.config)
    Logger.info('Wechat notification config saved')
  } catch (error) {
    Logger.error('Failed to save wechat notification config', error)
  }
}

/**
 * 添加接收者
 * @param {Object} recipient - 接收者信息 {openid, name, role}
 */
WechatNotificationManager.prototype.addRecipient = function(recipient) {
  if (!recipient.openid || !recipient.name) {
    throw new Error('接收者信息不完整')
  }

  // 检查是否已存在
  var exists = this.config.recipients.find(function(r) {
    return r.openid === recipient.openid
  })

  if (!exists) {
    this.config.recipients.push({
      openid: recipient.openid,
      name: recipient.name,
      role: recipient.role || 'user',
      enabled: true,
      addTime: new Date().toISOString()
    })
    this.saveConfig()
    Logger.info('Recipient added', { name: recipient.name, role: recipient.role })
  }
}

/**
 * 移除接收者
 * @param {string} openid - 接收者openid
 */
WechatNotificationManager.prototype.removeRecipient = function(openid) {
  var index = this.config.recipients.findIndex(function(r) {
    return r.openid === openid
  })

  if (index >= 0) {
    var removed = this.config.recipients.splice(index, 1)[0]
    this.saveConfig()
    Logger.info('Recipient removed', { name: removed.name })
  }
}

/**
 * 获取接收者列表
 * @param {string} role - 角色筛选 (可选)
 */
WechatNotificationManager.prototype.getRecipients = function(role) {
  if (role) {
    return this.config.recipients.filter(function(r) {
      return r.role === role && r.enabled
    })
  }
  return this.config.recipients.filter(function(r) {
    return r.enabled
  })
}

/**
 * 发送通知消息
 * @param {string} type - 消息类型
 * @param {Object} data - 消息数据
 * @param {Array} recipients - 指定接收者 (可选)
 */
WechatNotificationManager.prototype.sendNotification = function(type, data, recipients) {
  var self = this
  
  return new Promise(function(resolve, reject) {
    if (!self.config.enabled) {
      Logger.warn('Wechat notification is disabled')
      resolve({ success: false, message: '微信通知未启用' })
      return
    }

    var template = self.config.templates[type]
    if (!template) {
      Logger.error('Unknown notification type', { type: type })
      resolve({ success: false, message: '未知的通知类型' })
      return
    }

    // 确定接收者
    var targetRecipients = recipients || self.getRecipients()
    if (targetRecipients.length === 0) {
      Logger.warn('No recipients found for notification')
      resolve({ success: false, message: '没有找到接收者' })
      return
    }

    // 生成消息内容
    var message = self.generateMessage(template.template, data)
    
    // 发送给每个接收者
    var results = []
    var completed = 0
    
    function checkComplete() {
      completed++
      if (completed === targetRecipients.length) {
        var successCount = results.filter(function(r) { return r.success }).length
        Logger.info('Notification sent', { 
          type: type, 
          total: results.length, 
          success: successCount 
        })

        resolve({
          success: successCount > 0,
          message: successCount + '/' + results.length + ' 发送成功',
          results: results
        })
      }
    }
    
    for (var i = 0; i < targetRecipients.length; i++) {
      var recipient = targetRecipients[i]
      
      self.sendToUser(recipient.openid, template.title, message).then(function(result) {
        results.push({
          recipient: recipient.name,
          success: result.success,
          message: result.message
        })
        checkComplete()
      }).catch(function(error) {
        results.push({
          recipient: recipient.name,
          success: false,
          message: error.message
        })
        checkComplete()
      })
    }
  })
}

/**
 * 发送消息给指定用户
 * @param {string} openid - 用户openid
 * @param {string} title - 消息标题
 * @param {string} content - 消息内容
 */
WechatNotificationManager.prototype.sendToUser = function(openid, title, content) {
  // 调用后端API发送微信消息
  var app = getApp()
  
  return new Promise(function(resolve, reject) {
    app.request({
      url: '/wechat/send-message',
      method: 'POST',
      data: {
        openid: openid,
        title: title,
        content: content,
        page: 'pages/index/index',
        timestamp: new Date().toISOString()
      }
    }).then(function(response) {
      if (response.code === 200 && response.data && response.data.success) {
        resolve({ 
          success: true, 
          message: response.data.message || '发送成功' 
        })
      } else {
        resolve({ 
          success: false, 
          message: response.data ? response.data.message : (response.message || '发送失败')
        })
      }
    }).catch(function(error) {
      Logger.error('Failed to send message to user', { openid: openid, error: error })
      
      // 如果是404错误，提示用户检查后端服务
      if (error.statusCode === 404) {
        resolve({ 
          success: false, 
          message: '后端服务未启动或API接口不存在，请检查服务器配置' 
        })
      } else {
        resolve({ 
          success: false, 
          message: error.message || '网络错误，请检查网络连接' 
        })
      }
    })
  })
}

/**
 * 生成消息内容
 * @param {string} template - 消息模板
 * @param {Object} data - 数据
 */
WechatNotificationManager.prototype.generateMessage = function(template, data) {
  var message = template
  
  // 替换模板变量
  Object.keys(data).forEach(function(key) {
    var placeholder = '{{' + key + '}}'
    var value = data[key] || ''
    message = message.replace(new RegExp(placeholder.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), value)
  })

  return message
}

/**
 * 发送日志告警
 * @param {string} level - 日志级别
 * @param {string} message - 日志消息
 */
WechatNotificationManager.prototype.sendLogAlert = function(level, message) {
  return this.sendNotification('log_alert', {
    time: new Date().toLocaleString('zh-CN'),
    level: level,
    message: message
  })
}

/**
 * 发送每日报告
 * @param {Object} reportData - 报告数据
 */
WechatNotificationManager.prototype.sendDailyReport = function(reportData) {
  return this.sendNotification('daily_report', {
    date: reportData.date || new Date().toLocaleDateString('zh-CN'),
    piecework_count: reportData.pieceworkCount || 0,
    total_amount: reportData.totalAmount || '0.00',
    inventory_changes: reportData.inventoryChanges || 0
  })
}

/**
 * 发送系统告警
 * @param {string} type - 告警类型
 * @param {string} description - 告警描述
 */
WechatNotificationManager.prototype.sendSystemAlert = function(type, description) {
  // 只发送给管理员
  var adminRecipients = this.getRecipients('admin')
  
  return this.sendNotification('system_alert', {
    time: new Date().toLocaleString('zh-CN'),
    type: type,
    description: description
  }, adminRecipients)
}

/**
 * 发送库存告警
 * @param {Object} alertData - 告警数据
 */
WechatNotificationManager.prototype.sendInventoryAlert = function(alertData) {
  return this.sendNotification('inventory_alert', {
    time: new Date().toLocaleString('zh-CN'),
    product_name: alertData.productName,
    current_stock: alertData.currentStock,
    threshold: alertData.threshold
  })
}

/**
 * 启用/禁用通知
 * @param {boolean} enabled - 是否启用
 */
WechatNotificationManager.prototype.setEnabled = function(enabled) {
  this.config.enabled = enabled
  this.saveConfig()
  Logger.info('Wechat notification ' + (enabled ? 'enabled' : 'disabled'))
}

/**
 * 获取配置信息
 */
WechatNotificationManager.prototype.getConfig = function() {
  return {
    enabled: this.config.enabled,
    recipientCount: this.config.recipients.length,
    templates: Object.keys(this.config.templates)
  }
}

/**
 * 测试通知功能
 */
WechatNotificationManager.prototype.testNotification = function() {
  return this.sendNotification('system_alert', {
    time: new Date().toLocaleString('zh-CN'),
    type: '功能测试',
    description: '这是一条测试消息，用于验证微信通知功能是否正常工作。'
  })
}

// 创建全局实例
var wechatNotificationManager = new WechatNotificationManager()

module.exports = wechatNotificationManager