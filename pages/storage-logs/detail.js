// pages/storage-logs/detail.js
var app = getApp()

Page({
  data: {
    log: null,
    failedRecords: [],
    storageDetails: [],
    loading: true
  },

  onLoad(options) {
    if (!app.checkLogin()) return
    if (options.id) {
      this.loadDetail(options.id)
    }
  },

  loadDetail(id) {
    wx.showLoading({ title: '加载中...' })
    
    app.request({ url: '/storage-logs/' + id })
      .then(res => {
        wx.hideLoading()
        const data = res.data || {}
        
        // 解析JSON字段
        let storageDetails = []
        if (data.storage_details) {
          try {
            storageDetails = JSON.parse(data.storage_details)
          } catch (e) {
            console.error('解析storage_details失败', e)
          }
        }

        this.setData({
          log: data,
          failedRecords: data.failed_records || [],
          storageDetails: storageDetails,
          loading: false
        })
      })
      .catch(err => {
        wx.hideLoading()
        console.error('加载详情失败', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
        this.setData({ loading: false })
      })
  }
})
