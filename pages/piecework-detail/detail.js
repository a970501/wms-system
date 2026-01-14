// pages/piecework-detail/detail.js
var app = getApp()

Page({
  data: {
    record: null,
    recordId: null
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ recordId: options.id })
      this.loadDetail(options.id)
    } else if (options.data) {
      // 通过传递数据方式
      try {
        const record = JSON.parse(decodeURIComponent(options.data))
        this.setData({ record, recordId: record.id })
      } catch (e) {
        console.error('解析数据失败', e)
      }
    }
  },

  onShow() {
    // 页面显示时，如果有记录ID则重新加载数据（用于从编辑页返回后刷新）
    const { recordId } = this.data
    if (recordId) {
      this.loadDetail(recordId)
    }
  },

  loadDetail(id) {
    wx.showLoading({ title: '加载中...' })
    app.request({
      url: `/piecework/${id}`
    }).then(res => {
      wx.hideLoading()
      if (res.data) {
        this.setData({ record: res.data })
      }
    }).catch(err => {
      wx.hideLoading()
      console.error('加载详情失败', err)
      wx.showToast({ title: '加载失败', icon: 'none' })
    })
  },

  goToEdit() {
    const { record } = this.data
    if (record && record.id) {
      wx.navigateTo({
        url: `/pages/piecework-add/add?id=${record.id}`
      })
    }
  },

  handleDelete() {
    const { record, recordId } = this.data
    const id = record?.id || recordId
    
    if (!id) {
      wx.showToast({ title: '无法获取记录ID', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认删除',
      content: '删除后无法恢复，确定要删除这条计件记录吗？',
      confirmText: '删除',
      confirmColor: '#ff4d4f',
      success: (res) => {
        if (res.confirm) {
          this.doDelete(id)
        }
      }
    })
  },

  doDelete(id) {
    wx.showLoading({ title: '删除中...' })
    app.request({
      url: `/piecework/${id}`,
      method: 'DELETE'
    }).then(res => {
      wx.hideLoading()
      wx.showToast({ title: '删除成功', icon: 'success' })
      setTimeout(() => {
        wx.navigateBack()
      }, 1500)
    }).catch(err => {
      wx.hideLoading()
      console.error('删除失败', err)
      const msg = err.message || '删除失败'
      wx.showToast({ title: msg, icon: 'none' })
    })
  }
})

