// pages/index/index.js
var app = getApp()
var Logger = require('../../utils/logger')
var LoadingManager = require('../../utils/loading-manager')
var SmartCacheManager = require('../../utils/smart-cache-manager')
var OfflineManager = require('../../utils/offline-manager')
var DataLoader = require('../../utils/data-loader')

Page({
  data: {
    userInfo: {},
    currentDate: '',
    todayPiecework: 0,
    totalInventory: 0,
    recentRecords: []
  },

  onLoad: function() {
    Logger.info('Index page loaded')
    this.setCurrentDate()
  },

  onShow: function() {
    if (!app.checkLogin()) return
    
    this.setData({
      userInfo: app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    })
    
    this.loadData()
  },

  setCurrentDate: function() {
    var now = new Date()
    var year = now.getFullYear()
    var month = String(now.getMonth() + 1).padStart(2, '0')
    var day = String(now.getDate()).padStart(2, '0')
    this.setData({ currentDate: year + '-' + month + '-' + day })
  },

  loadData: function() {
    var self = this
    // 使用DataLoader进行优化的数据加载
    var today = this.data.currentDate
    
    // 批量预加载配置
    var preloadConfigs = [
      {
        type: 'single',
        url: '/piecework?startDate=' + today + '&endDate=' + today,
        options: {
          cache: true,
          cacheStrategy: SmartCacheManager.CACHE_STRATEGIES.DYNAMIC,
          transform: function(data) {
            var records = Array.isArray(data.content) ? data.content : []
            return records.reduce(function(sum, item) {
              return sum + (parseFloat(item.quantity) || 0)
            }, 0)
          }
        }
      },
      {
        type: 'list',
        url: '/piecework',
        options: {
          params: { page: 0, size: 5 },
          cache: true,
          cacheStrategy: SmartCacheManager.CACHE_STRATEGIES.DYNAMIC,
          maxItems: 5,
          transform: function(item) {
            return Object.assign({}, item, {
              displayTime: new Date(item.createTime || item.date).toLocaleDateString()
            })
          }
        }
      },
      {
        type: 'list',
        url: '/inventory/items',
        options: {
          cache: true,
          cacheStrategy: SmartCacheManager.CACHE_STRATEGIES.STATIC,
          transform: function(item) {
            return Object.assign({}, item, {
              quantity: parseFloat(item.quantity) || 0
            })
          }
        }
      }
    ]

    // 并发加载所有数据
    DataLoader.loadBatch(preloadConfigs, {
      concurrent: true,
      showLoading: false,
      failFast: false
    }).then(function(results) {
      // 处理今日计件数据
      var todayPiecework = results[0]
      if (todayPiecework !== null) {
        self.setData({ todayPiecework: todayPiecework })
      }

      // 处理最近记录
      var recentRecords = results[1]
      if (recentRecords !== null && Array.isArray(recentRecords)) {
        self.setData({ recentRecords: recentRecords })
      }

      // 处理库存总数
      var inventoryItems = results[2]
      if (inventoryItems !== null && Array.isArray(inventoryItems)) {
        var totalInventory = inventoryItems.reduce(function(sum, item) {
          return sum + item.quantity
        }, 0)
        self.setData({ totalInventory: totalInventory })
      }
    }).catch(function(error) {
      Logger.error('Failed to load index data', error)
      LoadingManager.showError('数据加载失败')
    })
  },

  loadTodayPiecework: function() {
    var self = this
    var today = this.data.currentDate
    var cacheKey = 'today_piecework_' + today
    
    // 先尝试从智能缓存获取
    var cachedData = SmartCacheManager.getWithStats(cacheKey)
    if (cachedData !== null) {
      this.setData({ todayPiecework: cachedData })
      return
    }

    OfflineManager.createOfflineRequest(app, {
      url: '/piecework?startDate=' + today + '&endDate=' + today
    }).then(function(res) {
      var data = res.data || {}
      var records = Array.isArray(data.content) ? data.content : []
      var total = records.reduce(function(sum, item) {
        var quantity = parseFloat(item.quantity) || 0
        return sum + quantity
      }, 0)
      
      self.setData({ todayPiecework: total })
      
      // 使用智能缓存策略
      SmartCacheManager.setWithStrategy(cacheKey, total, SmartCacheManager.CACHE_STRATEGIES.DYNAMIC)
    }).catch(function(err) {
      Logger.error('Failed to load today piecework', err)
      if (!self.data.isOnline) {
        LoadingManager.showWarning('离线模式：今日计件数据可能不是最新')
      }
    })
  },

  loadRecentRecords: function() {
    var self = this
    var cacheKey = 'recent_piecework_records'
    
    // 先尝试从智能缓存获取
    var cachedData = SmartCacheManager.getWithStats(cacheKey)
    if (cachedData !== null) {
      this.setData({ recentRecords: cachedData })
      return
    }

    OfflineManager.createOfflineRequest(app, {
      url: '/piecework?page=0&size=5'
    }).then(function(res) {
      var data = res.data || {}
      var records = Array.isArray(data.content) ? data.content : []
      var recentRecords = records.slice(0, 5)
      
      self.setData({ recentRecords: recentRecords })
      
      // 使用智能缓存策略
      SmartCacheManager.setWithStrategy(cacheKey, recentRecords, SmartCacheManager.CACHE_STRATEGIES.DYNAMIC)
    }).catch(function(err) {
      Logger.error('Failed to load recent records', err)
      if (!self.data.isOnline) {
        LoadingManager.showWarning('离线模式：最近记录可能不是最新')
      }
    })
  },

  loadTotalInventory: function() {
    var self = this
    var cacheKey = 'total_inventory'
    
    // 先尝试从智能缓存获取
    var cachedData = SmartCacheManager.getWithStats(cacheKey)
    if (cachedData !== null) {
      this.setData({ totalInventory: cachedData })
      return
    }

    OfflineManager.createOfflineRequest(app, {
      url: '/inventory/items'
    }).then(function(res) {
      var items = Array.isArray(res.data) ? res.data : []
      var partsTotal = items.reduce(function(sum, item) {
        var quantity = parseFloat(item.quantity) || 0
        return sum + quantity
      }, 0)
      
      self.setData({ totalInventory: partsTotal })
      
      // 使用智能缓存策略 - 库存数据相对静态
      SmartCacheManager.setWithStrategy(cacheKey, partsTotal, SmartCacheManager.CACHE_STRATEGIES.STATIC)
    }).catch(function(err) {
      Logger.error('Failed to load total inventory', err)
      self.setData({ totalInventory: 0 })
      if (!self.data.isOnline) {
        LoadingManager.showWarning('离线模式：库存数据可能不是最新')
      }
    })
  },

  goToPiecework: function() {
    wx.switchTab({ url: '/pages/piecework/piecework' })
  },

  goToInventory: function() {
    wx.switchTab({ url: '/pages/inventory/inventory' })
  },

  goToAddPiecework: function() {
    wx.navigateTo({ url: '/pages/piecework-add/add' })
  },

  goToMyPiecework: function() {
    wx.switchTab({ url: '/pages/piecework/piecework' })
  },

  refreshData: function() {
    var self = this
    LoadingManager.show('刷新中...')
    
    // 清除相关缓存
    var today = this.data.currentDate
    SmartCacheManager.remove('today_piecework_' + today)
    SmartCacheManager.remove('recent_piecework_records')
    SmartCacheManager.remove('total_inventory')
    
    // 重新加载数据
    this.loadData()
    
    setTimeout(function() {
      LoadingManager.showSuccess('刷新成功')
      LoadingManager.hide()
    }, 1000)
  },

  onPullDownRefresh: function() {
    var self = this
    this.refreshData()
    setTimeout(function() {
      wx.stopPullDownRefresh()
    }, 1500)
  },

  goToDetail: function(e) {
    var record = e.currentTarget.dataset.record
    if (record && record.id) {
      wx.navigateTo({
        url: '/pages/piecework-detail/detail?id=' + record.id
      })
    }
  },

  // 分享给好友
  onShareAppMessage: function() {
    return {
      title: '仓库管理系统 - 轻松管理库存和计件',
      path: '/pages/login/login',
      imageUrl: '' // 可选，分享图片
    }
  }
})