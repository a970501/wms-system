// pages/inventory-detail/detail.js
var app = getApp()

Page({
  data: {
    item: null,
    type: 'parts', // parts, blank, finished
    typeNames: {
      parts: '零件库存',
      blank: '毛坯库存',
      finished: '成品库存'
    },
    showAdjustModal: false,
    adjustType: 'add', // add or reduce
    adjustQuantity: '',
    adjustRemark: '',
    history: []
  },

  onLoad(options) {
    console.log('详情页加载，参数:', options)
    
    if (options.type) {
      this.setData({ type: options.type })
      wx.setNavigationBarTitle({ title: this.data.typeNames[options.type] + '详情' })
    }

    if (options.id && options.type) {
      this.setData({ itemId: options.id })
      this.loadDetail(options.id, options.type)
    } else if (options.data) {
      try {
        const item = JSON.parse(decodeURIComponent(options.data))
        console.log('解析到的item:', item)
        this.setData({ item, itemId: item.id })
        
        // 加载历史记录（仅零件库存）
        if (options.type === 'parts' && item.id) {
          console.log('准备加载历史记录，ID:', item.id)
          this.loadHistory(item.id)
        }
      } catch (e) {
        console.error('解析数据失败', e)
      }
    }
  },

  loadDetail(id, type) {
    let url = ''
    switch (type) {
      case 'parts':
        url = `/inventory/items/${id}`
        break
      case 'blank':
        url = `/blank-inventory/${id}`
        break
      case 'finished':
        url = `/finished-products/${id}`
        break
    }

    wx.showLoading({ title: '加载中...' })
    app.request({ url })
      .then(res => {
        wx.hideLoading()
        if (res.data) {
          this.setData({ item: res.data })
          // 加载历史记录（仅零件库存有历史记录）
          if (type === 'parts') {
            this.loadHistory(id)
          }
        }
      })
      .catch(err => {
        wx.hideLoading()
        console.error('加载详情失败', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
      })
  },

  loadHistory(id) {
    console.log('开始加载历史记录，ID:', id)
    app.request({ url: `/inventory/items/${id}/history` })
      .then(res => {
        console.log('历史记录响应:', res)
        if (res.data && res.data.length > 0) {
          console.log('历史记录数据:', res.data)
          // 格式化时间
          const formattedHistory = res.data.map(item => ({
            ...item,
            created_at: this.formatDateTime(item.created_at)
          }))
          this.setData({ history: formattedHistory })
        } else {
          console.log('历史记录数据为空')
          this.setData({ history: [] })
        }
      })
      .catch(err => {
        console.error('加载历史记录失败', err)
        wx.showToast({ title: '加载历史失败: ' + (err.message || '未知错误'), icon: 'none' })
      })
  },

  // 格式化时间
  formatDateTime(dateStr) {
    if (!dateStr) return ''
    const date = new Date(dateStr)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hour = String(date.getHours()).padStart(2, '0')
    const minute = String(date.getMinutes()).padStart(2, '0')
    return `${year}-${month}-${day} ${hour}:${minute}`
  },

  // 快捷调整数量
  quickAdjust(e) {
    const type = e.currentTarget.dataset.type
    this.setData({
      showAdjustModal: true,
      adjustType: type,
      adjustQuantity: '',
      adjustRemark: ''
    })
  },

  closeModal() {
    this.setData({ showAdjustModal: false })
  },

  onAdjustInput(e) {
    this.setData({ adjustQuantity: e.detail.value })
  },

  onRemarkInput(e) {
    this.setData({ adjustRemark: e.detail.value })
  },

  confirmAdjust() {
    const { adjustQuantity, adjustType, item, type } = this.data
    const qty = parseInt(adjustQuantity)

    if (!qty || qty <= 0) {
      wx.showToast({ title: '请输入有效数量', icon: 'none' })
      return
    }

    const newQuantity = adjustType === 'add'
      ? item.quantity + qty
      : item.quantity - qty

    this.updateQuantity(newQuantity)
  },

  updateQuantity(newQuantity) {
    const { item, type, itemId } = this.data
    let url = ''

    switch (type) {
      case 'parts':
        url = `/inventory/items/${itemId}`
        break
      case 'blank':
        url = `/blank-inventory/${itemId}`
        break
      case 'finished':
        url = `/finished-products/${itemId}`
        break
    }

    wx.showLoading({ title: '更新中...' })
    app.request({
      url,
      method: 'PUT',
      data: { ...item, quantity: newQuantity }
    })
    .then(res => {
      wx.hideLoading()
      this.setData({
        showAdjustModal: false,
        'item.quantity': newQuantity
      })
      wx.showToast({ title: '更新成功', icon: 'success' })
    })
    .catch(err => {
      wx.hideLoading()
      console.error('更新失败', err)
      wx.showToast({ title: '更新失败', icon: 'none' })
    })
  },

  // 编辑
  editItem() {
    const { item, type } = this.data
    wx.navigateTo({
      url: `/pages/inventory-add/add?type=${type}&id=${item.id}&edit=true&data=${encodeURIComponent(JSON.stringify(item))}`
    })
  },

  // 删除
  deleteItem() {
    const { item, type, itemId } = this.data

    wx.showModal({
      title: '确认删除',
      content: `确定要删除"${item.productName}"吗？此操作不可恢复。`,
      confirmColor: '#F56C6C',
      success: (res) => {
        if (res.confirm) {
          this.doDelete()
        }
      }
    })
  },

  doDelete() {
    const { type, itemId } = this.data
    let url = ''

    switch (type) {
      case 'parts':
        url = `/inventory/items/${itemId}`
        break
      case 'blank':
        url = `/blank-inventory/${itemId}`
        break
    }

    wx.showLoading({ title: '删除中...' })
    app.request({ url, method: 'DELETE' })
      .then(res => {
        wx.hideLoading()
        wx.showToast({ title: '删除成功', icon: 'success' })
        setTimeout(() => {
          wx.navigateBack()
        }, 1500)
      })
      .catch(err => {
        wx.hideLoading()
        console.error('删除失败', err)
        wx.showToast({ title: '删除失败', icon: 'none' })
      })
  }
})

