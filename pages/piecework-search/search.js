// pages/piecework-search/search.js
var app = getApp()

Page({
  data: {
    // 筛选选项
    workerNames: ['全部'],
    productNames: ['全部'],
    specifications: ['全部'],
    materials: ['全部', '304', '304重', '316', '316重'],
    // 选中索引
    workerIndex: 0,
    productIndex: 0,
    specIndex: 0,
    materialIndex: 0,
    // 选中值
    selectedWorker: '',
    selectedProduct: '',
    selectedSpec: '',
    selectedMaterial: '',
    // 日期
    startDate: '',
    endDate: '',
    // 快捷筛选
    onlySemi: false,
    onlyFinished: false,
    onlyDefect: false,
    // 结果
    searched: false,
    records: [],
    totalQuantity: 0,
    totalAmount: '0.00',
    // 导出状态
    exporting: false
  },

  onLoad: function() {
    this.initDates()
    this.loadFilterOptions()
  },

  initDates: function() {
    var now = new Date()
    var year = now.getFullYear()
    var month = String(now.getMonth() + 1).padStart(2, '0')
    this.setData({
      startDate: year + '-' + month + '-01',
      endDate: year + '-' + month + '-' + String(now.getDate()).padStart(2, '0')
    })
  },

  loadFilterOptions: function() {
    var self = this

    // 使用新的工人列表接口（所有用户可访问）
    app.request({ url: '/users/workers' }).then(function(res) {
      var workers = res.data || []
      var workerNames = workers.map(function(w) {
        return w.displayName || w.username
      })
      var names = ['全部'].concat(workerNames)
      self.setData({ workerNames: names, userList: workers })
    }).catch(function(err) {
      console.error('获取工人列表失败', err)
      self.setData({ workerNames: ['全部'], userList: [] })
    })

    // 加载产品名称和规格
    app.request({ url: '/price-tables' }).then(function(res) {
      var tables = res.data || []
      var productNameSet = new Set(tables.map(function(t) { return t.productName }))
      var specificationSet = new Set(tables.map(function(t) { return t.specification }))
      var products = ['全部'].concat(Array.from(productNameSet))
      var specs = ['全部'].concat(Array.from(specificationSet))
      self.setData({ productNames: products, specifications: specs })
    }).catch(function() {})
  },

  onWorkerChange: function(e) {
    var idx = e.detail.value
    this.setData({
      workerIndex: idx,
      selectedWorker: idx == 0 ? '' : this.data.workerNames[idx]
    })
  },

  onProductChange: function(e) {
    var idx = e.detail.value
    this.setData({
      productIndex: idx,
      selectedProduct: idx == 0 ? '' : this.data.productNames[idx]
    })
  },

  onSpecChange: function(e) {
    var idx = e.detail.value
    this.setData({
      specIndex: idx,
      selectedSpec: idx == 0 ? '' : this.data.specifications[idx]
    })
  },

  onMaterialChange: function(e) {
    var idx = e.detail.value
    this.setData({
      materialIndex: idx,
      selectedMaterial: idx == 0 ? '' : this.data.materials[idx]
    })
  },

  onStartDateChange: function(e) {
    this.setData({ startDate: e.detail.value })
  },

  onEndDateChange: function(e) {
    this.setData({ endDate: e.detail.value })
  },

  toggleSemi: function() {
    this.setData({ onlySemi: !this.data.onlySemi, onlyFinished: false })
  },

  toggleFinished: function() {
    this.setData({ onlyFinished: !this.data.onlyFinished, onlySemi: false })
  },

  toggleDefect: function() {
    this.setData({ onlyDefect: !this.data.onlyDefect })
  },

  resetFilter: function() {
    this.setData({
      workerIndex: 0, productIndex: 0, specIndex: 0, materialIndex: 0,
      selectedWorker: '', selectedProduct: '', selectedSpec: '', selectedMaterial: '',
      onlySemi: false, onlyFinished: false, onlyDefect: false,
      searched: false, records: []
    })
    this.initDates()
  },

  doSearch: function() {
    var self = this
    wx.showLoading({ title: '查询中...' })
    var startDate = this.data.startDate
    var endDate = this.data.endDate

    // 所有用户都可以查询所有数据
    var url = '/piecework?startDate=' + startDate + '&endDate=' + endDate + '&queryAll=true'

    console.log('【高级查询】请求URL:', url)

    app.request({ url: url }).then(function(res) {
      console.log('【高级查询】返回数据:', res)

      // 兼容多种返回格式
      var records = []
      if (Array.isArray(res.data)) {
        records = res.data
      } else if (res.data && Array.isArray(res.data.content)) {
        records = res.data.content
      } else if (res.data && typeof res.data === 'object') {
        records = res.data.records || res.data.list || []
      }

      console.log('【高级查询】解析记录数:', records.length)

      records = self.applyFilters(records)
      console.log('【高级查询】应用筛选后记录数:', records.length)

      self.calcStats(records)
      self.setData({ records: records, searched: true })
    }).catch(function(err) {
      console.error('查询失败', err)
      wx.showToast({ title: '查询失败', icon: 'none' })
      self.setData({ records: [], searched: true })
    }).finally(function() {
      wx.hideLoading()
    })
  },

  applyFilters: function(records) {
    var selectedWorker = this.data.selectedWorker
    var selectedProduct = this.data.selectedProduct
    var selectedSpec = this.data.selectedSpec
    var selectedMaterial = this.data.selectedMaterial
    var onlySemi = this.data.onlySemi
    var onlyFinished = this.data.onlyFinished
    var onlyDefect = this.data.onlyDefect
    var workerList = this.data.workerList

    console.log('【筛选条件】', { selectedWorker: selectedWorker, selectedProduct: selectedProduct, selectedSpec: selectedSpec, selectedMaterial: selectedMaterial, onlySemi: onlySemi, onlyFinished: onlyFinished, onlyDefect: onlyDefect })
    console.log('【工人列表】workerList:', workerList)
    if (records.length > 0) {
      console.log('【数据样本】前3条记录的工人字段:', records.slice(0, 3).map(function(item) {
        return {
          workerName: item.workerName,
          worker_name: item.worker_name,
          worker: item.worker,
          userId: item.userId,
          user_id: item.user_id
        }
      }))
    }

    return records.filter(function(item) {
      // 工人姓名筛选 - 兼容多种字段名
      var workerName = item.workerName || item.worker_name || item.worker || ''
      if (selectedWorker && workerName !== selectedWorker) {
        return false
      }
      if (selectedProduct && item.productName !== selectedProduct) return false
      if (selectedSpec && item.specification !== selectedSpec) return false
      if (selectedMaterial && item.material !== selectedMaterial) return false
      if (onlySemi && item.semiFinished !== '是') return false
      if (onlyFinished && item.semiFinished === '是') return false
      if (onlyDefect && !(item.defectQuantity > 0)) return false
      return true
    })
  },

  calcStats: function(records) {
    var qty = 0
    var amtCents = 0
    records.forEach(function(item) {
      qty += parseInt(item.quantity) || 0
      amtCents += Math.round((parseFloat(item.totalAmount) || 0) * 100)
    })
    this.setData({ totalQuantity: qty, totalAmount: (amtCents / 100).toFixed(2) })
  },

  goToDetail: function(e) {
    var record = e.currentTarget.dataset.record
    if (record && record.id) {
      wx.navigateTo({ url: '/pages/piecework-detail/detail?id=' + record.id })
    }
  },

  onPullDownRefresh: function() {
    if (this.data.searched) {
      this.doSearch()
    }
    wx.stopPullDownRefresh()
  },

  // 导出Excel功能
  exportToExcel: function() {
    var self = this
    var records = this.data.records
    
    if (!records || records.length === 0) {
      wx.showToast({ title: '没有数据可导出', icon: 'none' })
      return
    }

    // 显示导出选项
    wx.showActionSheet({
      itemList: ['导出当前筛选结果', '导出详细报表'],
      success: function(res) {
        if (res.tapIndex === 0) {
          self.exportCurrentResults()
        } else if (res.tapIndex === 1) {
          self.exportDetailedReport()
        }
      }
    })
  },

  // 导出当前筛选结果
  exportCurrentResults: function() {
    var self = this
    this.setData({ exporting: true })
    
    wx.showLoading({ title: '生成Excel中...' })
    
    // 准备导出数据
    var exportData = {
      records: this.data.records,
      filters: {
        worker: this.data.selectedWorker,
        product: this.data.selectedProduct,
        specification: this.data.selectedSpec,
        material: this.data.selectedMaterial,
        startDate: this.data.startDate,
        endDate: this.data.endDate,
        onlySemi: this.data.onlySemi,
        onlyFinished: this.data.onlyFinished,
        onlyDefect: this.data.onlyDefect
      },
      summary: {
        totalRecords: this.data.records.length,
        totalQuantity: this.data.totalQuantity,
        totalAmount: this.data.totalAmount
      }
    }
    
    // 使用app.request方法生成下载链接
    app.request({
      url: '/export/piecework-excel',
      method: 'POST',
      data: exportData
    }).then(function(res) {
      if (res.data && res.data.success) {
        self.showDownloadDialog(res.data)
      } else {
        throw new Error(res.data ? res.data.message : '生成Excel文件失败')
      }
    }).catch(function(err) {
      console.error('导出失败', err)
      wx.showModal({
        title: '导出失败',
        content: err.message || '生成Excel文件失败，请重试',
        showCancel: false
      })
    }).finally(function() {
      wx.hideLoading()
      self.setData({ exporting: false })
    })
  },

  // 导出详细报表
  exportDetailedReport: function() {
    var self = this
    this.setData({ exporting: true })
    
    wx.showLoading({ title: '生成详细报表中...' })
    
    // 准备详细报表数据
    var reportData = {
      records: this.data.records,
      filters: {
        worker: this.data.selectedWorker,
        product: this.data.selectedProduct,
        specification: this.data.selectedSpec,
        material: this.data.selectedMaterial,
        startDate: this.data.startDate,
        endDate: this.data.endDate,
        onlySemi: this.data.onlySemi,
        onlyFinished: this.data.onlyFinished,
        onlyDefect: this.data.onlyDefect
      },
      includeStatistics: true,
      includeCharts: true
    }
    
    // 使用app.request方法生成下载链接
    app.request({
      url: '/export/piecework-detailed-report',
      method: 'POST',
      data: reportData
    }).then(function(res) {
      if (res.data && res.data.success) {
        self.showDownloadDialog(res.data)
      } else {
        throw new Error(res.data ? res.data.message : '生成详细报表失败')
      }
    }).catch(function(err) {
      console.error('导出详细报表失败', err)
      wx.showModal({
        title: '导出失败',
        content: err.message || '生成详细报表失败，请重试',
        showCancel: false
      })
    }).finally(function() {
      wx.hideLoading()
      self.setData({ exporting: false })
    })
  },

  // 显示下载对话框
  showDownloadDialog: function(downloadInfo) {
    var downloadUrl = app.globalData.baseUrl + downloadInfo.downloadUrl
    var fileSize = this.formatFileSize(downloadInfo.fileSize)
    
    wx.showModal({
      title: '📊 Excel文件已生成',
      content: '文件名：' + downloadInfo.filename + '\n' +
               '文件大小：' + fileSize + '\n\n' +
               '请选择下载方式：',
      confirmText: '浏览器下载',
      cancelText: '复制链接',
      success: function(res) {
        if (res.confirm) {
          // 浏览器下载
          wx.showModal({
            title: '浏览器下载',
            content: '请在浏览器中打开以下链接下载文件：\n\n' + downloadUrl + '\n\n点击"复制链接"可复制到剪贴板',
            confirmText: '复制链接',
            cancelText: '知道了',
            success: function(modalRes) {
              if (modalRes.confirm) {
                wx.setClipboardData({
                  data: downloadUrl,
                  success: function() {
                    wx.showToast({
                      title: '链接已复制',
                      icon: 'success'
                    })
                  }
                })
              }
            }
          })
        } else {
          // 直接复制链接
          wx.setClipboardData({
            data: downloadUrl,
            success: function() {
              wx.showToast({
                title: '链接已复制',
                icon: 'success'
              })
              wx.showModal({
                title: '提示',
                content: '下载链接已复制到剪贴板，请在浏览器中粘贴打开下载',
                showCancel: false
              })
            }
          })
        }
      }
    })
  },

  // 格式化文件大小
  formatFileSize: function(bytes) {
    if (bytes === 0) return '0 B'
    var k = 1024
    var sizes = ['B', 'KB', 'MB', 'GB']
    var i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  },

  onPullDownRefresh: function() {
    if (this.data.searched) {
      this.doSearch()
    }
    wx.stopPullDownRefresh()
  }
})

