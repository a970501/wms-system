// pages/storage-logs/logs.js
var app = getApp()
var Logger = require('../../utils/logger')

Page({
  data: {
    logs: [],
    statistics: {},
    page: 0,
    size: 20,
    hasMore: true,
    loading: false
  },

  onLoad() {
    if (!app.checkLogin()) return
    this.loadStatistics()
    this.loadLogs()
  },

  onPullDownRefresh() {
    this.setData({ page: 0, logs: [], hasMore: true })
    this.loadStatistics()
    this.loadLogs()
    wx.stopPullDownRefresh()
  },

  loadStatistics: function() {
    var self = this
    app.request({ url: '/storage-logs/statistics' })
      .then(function(res) {
        self.setData({ statistics: res.data || {} })
      })
      .catch(function(err) {
        Logger.error('Failed to load statistics', err)
      })
  },

  loadLogs: function() {
    var self = this
    if (this.data.loading || !this.data.hasMore) return

    this.setData({ loading: true })
    
    app.request({
      url: '/storage-logs?page=' + this.data.page + '&size=' + this.data.size
    })
      .then(function(res) {
        var logs = res.data || []
        var allLogs = self.data.page === 0 ? logs : self.data.logs.concat(logs)
        
        self.setData({
          logs: allLogs,
          hasMore: logs.length >= self.data.size,
          loading: false
        })
      })
      .catch(function(err) {
        Logger.error('Failed to load logs', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
        self.setData({ loading: false })
      })
  },

  loadMore: function() {
    this.setData({ page: this.data.page + 1 })
    this.loadLogs()
  },

  viewDetail: function(e) {
    var id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: '/pages/storage-logs/detail?id=' + id
    })
  }
})
