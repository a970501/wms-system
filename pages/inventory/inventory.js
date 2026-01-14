// pages/inventory/inventory.js
var app = getApp()

Page({
  data: {
    currentTab: 'parts',
    keyword: '',
    inventoryList: [],
    page: 0,
    size: 20,
    hasMore: true
  },

  onShow() {
    if (!app.checkLogin()) return
    this.loadInventory()
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({
      currentTab: tab,
      inventoryList: [],
      page: 0,
      hasMore: true
    })
    this.loadInventory()
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  searchInventory() {
    this.setData({ page: 0, inventoryList: [], hasMore: true })
    this.loadInventory()
  },

  loadInventory() {
    const currentTab = this.data.currentTab
    const keyword = this.data.keyword
    const page = this.data.page
    const size = this.data.size
    wx.showLoading({ title: '加载中...' })

    let url = ''
    switch (currentTab) {
      case 'parts':
        url = '/inventory/items'
        break
      case 'blank':
        url = '/blank-inventory'
        break
      case 'finished':
        url = '/finished-products'
        break
    }

    url += '?page=' + page + '&size=' + size
    if (keyword) url += '&keyword=' + encodeURIComponent(keyword)

    app.request({ url })
      .then(function(res) {
        // 后端返回: {code: 200, data: {content: [...], ...}} 或 {code: 200, data: [...]}
        let newItems = []
        if (res.data) {
          if (Array.isArray(res.data)) {
            newItems = res.data
          } else if (res.data.content) {
            newItems = res.data.content
          }
        }
        const allItems = page === 0 ? newItems : this.data.inventoryList.concat(newItems)

        this.setData({
          inventoryList: allItems,
          hasMore: newItems.length >= size
        })
      }.bind(this))
      .catch(function(err) {
        console.error('加载库存失败', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
      })
      .finally(function() {
        wx.hideLoading()
      })
  },

  loadMore() {
    if (!this.data.hasMore) return
    this.setData({ page: this.data.page + 1 })
    this.loadInventory()
  },

  viewDetail(e) {
    const item = e.currentTarget.dataset.item
    const type = this.data.currentTab
    // 通过传递数据方式跳转到详情页
    const data = encodeURIComponent(JSON.stringify(item))
    wx.navigateTo({
      url: '/pages/inventory-detail/detail?type=' + type + '&data=' + data
    })
  },

  addInventory() {
    const type = this.data.currentTab
    wx.navigateTo({
      url: '/pages/inventory-add/add?type=' + type
    })
  },

  goAssembly() {
    wx.navigateTo({
      url: '/pages/assembly/assembly'
    })
  },

  onPullDownRefresh() {
    this.searchInventory()
    wx.stopPullDownRefresh()
  }
})

