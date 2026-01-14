// pages/audit-logs/logs.js
var app = getApp()

Page({
  data: {
    logs: [],
    statistics: {},
    modules: ['全部模块'],
    moduleIndex: 0,
    selectedModule: '',
    page: 0,
    size: 20,
    hasMore: true,
    loading: false
  },

  onLoad() {
    if (!app.checkLogin()) return
    this.loadModules()
    this.loadStatistics()
    this.loadLogs()
  },

  onPullDownRefresh() {
    this.setData({ page: 0, logs: [], hasMore: true })
    this.loadStatistics()
    this.loadLogs()
    wx.stopPullDownRefresh()
  },

  loadModules() {
    app.request({ url: '/audit-logs/modules' })
      .then(res => {
        const modules = ['全部模块', ...(res.data || [])]
        this.setData({ modules })
      })
      .catch(err => {
        console.error('加载模块列表失败', err)
      })
  },

  loadStatistics() {
    app.request({ url: '/audit-logs/statistics' })
      .then(res => {
        this.setData({ statistics: res.data || {} })
      })
      .catch(err => {
        console.error('加载统计失败', err)
      })
  },

  loadLogs() {
    if (this.data.loading || !this.data.hasMore) return

    this.setData({ loading: true })
    
    let url = `/audit-logs?page=${this.data.page}&size=${this.data.size}`
    if (this.data.selectedModule) {
      url += `&module=${encodeURIComponent(this.data.selectedModule)}`
    }
    
    app.request({ url })
      .then(res => {
        const data = res.data || {}
        const logs = data.content || []
        const allLogs = this.data.page === 0 ? logs : [...this.data.logs, ...logs]
        
        this.setData({
          logs: allLogs,
          hasMore: logs.length >= this.data.size,
          loading: false
        })
      })
      .catch(err => {
        console.error('加载日志失败', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
        this.setData({ loading: false })
      })
  },

  loadMore() {
    this.setData({ page: this.data.page + 1 })
    this.loadLogs()
  },

  onModuleChange(e) {
    const index = e.detail.value
    const module = index > 0 ? this.data.modules[index] : ''
    this.setData({ 
      moduleIndex: index,
      selectedModule: module,
      page: 0,
      logs: [],
      hasMore: true
    })
    this.loadLogs()
  },

  clearFilters() {
    this.setData({
      moduleIndex: 0,
      selectedModule: '',
      page: 0,
      logs: [],
      hasMore: true
    })
    this.loadLogs()
  }
})
