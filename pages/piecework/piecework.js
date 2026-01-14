// pages/piecework/piecework.js
var app = getApp()
var Logger = require('../../utils/logger')

Page({
  data: {
    keyword: '',
    startDate: '',
    endDate: '',
    records: [],
    allRecords: [], // 保存所有记录用于本地搜索
    totalQuantity: 0,
    totalAmount: '0.00',
    page: 0,
    size: 20,
    hasMore: true
  },

  onLoad: function() {
    this.initDates()
  },

  onShow: function() {
    if (!app.checkLogin()) return
    // 重置分页，防止数据累加
    this.setData({ page: 0, records: [], allRecords: [], hasMore: true })
    this.loadRecords()
  },

  initDates: function() {
    var now = new Date()
    var year = now.getFullYear()
    var month = String(now.getMonth() + 1).padStart(2, '0')
    var day = String(now.getDate()).padStart(2, '0')
    var today = year + '-' + month + '-' + day

    // 默认查询本月
    var firstDay = year + '-' + month + '-01'
    this.setData({
      startDate: firstDay,
      endDate: today
    })
  },

  onKeywordInput: function(e) {
    this.setData({ keyword: e.detail.value })
    // 实时搜索
    this.filterRecords()
  },

  clearKeyword: function() {
    this.setData({ keyword: '' })
    this.filterRecords()
  },

  onStartDateChange: function(e) {
    this.setData({ startDate: e.detail.value })
  },

  onEndDateChange: function(e) {
    this.setData({ endDate: e.detail.value })
  },

  searchRecords: function() {
    this.setData({ page: 0, records: [], allRecords: [], hasMore: true, keyword: '' })
    this.loadRecords()
  },

  filterRecords: function() {
    var keyword = this.data.keyword
    var allRecords = this.data.allRecords
    
    if (!keyword) {
      // 无搜索关键词时，重新加载总计
      this.setData({ records: allRecords })
      this.loadTotalStats()
      return
    }

    var kw = keyword.toLowerCase()
    var filtered = allRecords.filter(function(item) {
      return (item.productName && item.productName.toLowerCase().includes(kw)) ||
             (item.specification && item.specification.toLowerCase().includes(kw)) ||
             (item.material && item.material.toLowerCase().includes(kw))
    })

    // 搜索时显示过滤后的统计
    var totalQty = 0
    var totalAmtCents = 0
    filtered.forEach(function(item) {
      totalQty += parseInt(item.quantity) || 0
      totalAmtCents += Math.round((parseFloat(item.totalAmount) || 0) * 100)
    })
    
    this.setData({
      records: filtered,
      totalQuantity: totalQty,
      totalAmount: (totalAmtCents / 100).toFixed(2)
    })
  },

  // 加载所有记录的统计（不分页）
  loadTotalStats: function() {
    var self = this
    var startDate = this.data.startDate
    var endDate = this.data.endDate
    var url = '/piecework?startDate=' + startDate + '&endDate=' + endDate

    app.request({ url: url })
      .then(function(res) {
        // 不带分页参数返回数组格式
        var records = []
        if (Array.isArray(res.data)) {
          records = res.data
        } else if (res.data && res.data.content) {
          records = res.data.content
        }

        // 统计计件数量和金额
        var totalQty = 0
        var totalAmtCents = 0
        records.forEach(function(item) {
          totalQty += parseInt(item.quantity) || 0
          totalAmtCents += Math.round((parseFloat(item.totalAmount) || 0) * 100)
        })
        
        self.setData({
          allRecords: records,
          totalQuantity: totalQty,
          totalAmount: (totalAmtCents / 100).toFixed(2)
        })
      })
  },

  loadRecords: function() {
    var self = this
    var startDate = this.data.startDate
    var endDate = this.data.endDate
    var page = this.data.page
    var size = this.data.size
    
    wx.showLoading({ title: '加载中...' })

    // 第一页时同时加载总计
    if (page === 0) {
      this.loadTotalStats()
    }

    var url = '/piecework?page=' + page + '&size=' + size
    if (startDate) url += '&startDate=' + startDate
    if (endDate) url += '&endDate=' + endDate

    app.request({ url: url })
      .then(function(res) {
        // 后端返回: {code: 200, data: {content: [...], ...}}
        var data = res.data || {}
        var newRecords = data.content || []
        var allRecords = page === 0 ? newRecords : self.data.allRecords.concat(newRecords)

        self.setData({
          records: page === 0 ? newRecords : self.data.records.concat(newRecords),
          allRecords: allRecords,
          hasMore: newRecords.length >= size
        })
      })
      .catch(function(err) {
        Logger.error('加载计件记录失败', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
      })
      .finally(function() {
        wx.hideLoading()
      })
  },

  loadMore: function() {
    if (!this.data.hasMore) return
    this.setData({ page: this.data.page + 1 })
    this.loadRecords()
  },

  onReachBottom: function() {
    this.loadMore()
  },

  onPullDownRefresh: function() {
    this.setData({ page: 0, records: [], allRecords: [], hasMore: true })
    this.loadRecords()
    wx.stopPullDownRefresh()
  },

  goToDetail: function(e) {
    var record = e.currentTarget.dataset.record
    if (record && record.id) {
      wx.navigateTo({
        url: '/pages/piecework-detail/detail?id=' + record.id
      })
    }
  },

  // 导出记录
  exportRecords: function() {
    var records = this.data.records
    var startDate = this.data.startDate
    var endDate = this.data.endDate
    var totalQuantity = this.data.totalQuantity
    var totalAmount = this.data.totalAmount

    if (records.length === 0) {
      wx.showToast({ title: '暂无数据可导出', icon: 'none' })
      return
    }

    var self = this
    wx.showActionSheet({
      itemList: ['复制到剪贴板', '分享给好友'],
      success: function(res) {
        if (res.tapIndex === 0) {
          self.copyToClipboard()
        } else if (res.tapIndex === 1) {
          // 分享功能由onShareAppMessage处理
        }
      }
    })
  },

  // 复制到剪贴板
  copyToClipboard: function() {
    var records = this.data.records
    var startDate = this.data.startDate
    var endDate = this.data.endDate
    var totalQuantity = this.data.totalQuantity
    var totalAmount = this.data.totalAmount

    // 构建表格文本
    var text = '计件记录报表\n'
    text += '导出时间：' + new Date().toLocaleString() + '\n'
    text += '统计周期：' + startDate + ' 至 ' + endDate + '\n'
    text += '总数量：' + totalQuantity + '  总金额：¥' + totalAmount + '\n'
    text += '==================================================\n\n'
    
    // 表头
    text += '工作时间\t产品名称\t规格\t材质\t数量\t单价\t金额\n'
    text += '----------------------------------------------------------------------\n'

    // 数据行 - 时间精确到毫秒
    records.forEach(function(item) {
      // workDate 格式为 "2025-12-05T18:09:08.858724" 或 "2025-12-05 18:09:08.858724"
      var workTime = item.workDate || ''
      
      // 转换为易读格式
      if (workTime) {
        workTime = workTime.replace('T', ' ')
        // 确保显示毫秒（保留到3位小数）
        if (workTime.includes('.')) {
          var parts = workTime.split('.')
          if (parts[1] && parts[1].length > 3) {
            workTime = parts[0] + '.' + parts[1].substring(0, 3)
          }
        }
      }
      
      text += workTime + '\t' + (item.productName || '') + '\t' + (item.specification || '') + '\t'
      text += (item.material || '') + '\t' + (item.quantity || 0) + '\t¥' + (item.unitPrice || '0.00') + '\t¥' + (item.totalAmount || '0.00') + '\n'
    })
    
    text += '\n' + '='.repeat(50) + '\n'
    text += '合计：' + records.length + '条记录，' + totalQuantity + '件，¥' + totalAmount

    wx.setClipboardData({
      data: text,
      success: function() {
        wx.showToast({ title: '已复制到剪贴板', icon: 'success' })
      }
    })
  },

  goToAdd: function() {
    wx.navigateTo({ url: '/pages/piecework-add/add' })
  },

  goToSearch: function() {
    wx.navigateTo({ url: '/pages/piecework-search/search' })
  },

  // 页面分享
  onShareAppMessage: function() {
    var startDate = this.data.startDate
    var endDate = this.data.endDate
    var totalQuantity = this.data.totalQuantity
    var totalAmount = this.data.totalAmount

    return {
      title: '计件记录：' + startDate + '至' + endDate + '，共' + totalQuantity + '件，¥' + totalAmount,
      path: '/pages/piecework/piecework'
    }
  }
})