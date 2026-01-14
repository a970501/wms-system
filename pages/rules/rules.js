// pages/rules/rules.js
var app = getApp()

Page({
  data: {
    ruleType: 'storage', // storage 或 assembly
    rules: [],
    showModal: false,
    isEdit: false,
    currentId: null,
    formData: {},
    storageRatios: ['1:1', '1:2', '2:1', '1:3', '3:1'],
    storageRatioIndex: 0
  },

  onLoad(options) {
    if (!app.checkLogin()) return
    const type = options.type || 'storage'
    this.setData({ ruleType: type })
    wx.setNavigationBarTitle({ title: type === 'storage' ? '入库规则' : '装配规则' })
  },

  onShow() {
    this.loadRules()
  },

  switchTab(e) {
    const type = e.currentTarget.dataset.type
    this.setData({ ruleType: type, rules: [] })
    wx.setNavigationBarTitle({ title: type === 'storage' ? '入库规则' : '装配规则' })
    this.loadRules()
  },

  loadRules() {
    const url = this.data.ruleType === 'storage' ? '/auto-storage-rules' : '/assembly-rules'
    wx.showLoading({ title: '加载中...' })
    app.request({ url }).then(res => {
      this.setData({ rules: res.data || [] })
    }).catch(err => {
      console.error('加载规则失败', err)
      wx.showToast({ title: '加载失败', icon: 'none' })
    }).finally(() => wx.hideLoading())
  },

  toggleRule(e) {
    const id = e.currentTarget.dataset.id
    const url = this.data.ruleType === 'storage' 
      ? `/auto-storage-rules/${id}/toggle` 
      : `/assembly-rules/${id}/toggle`
    app.request({ url, method: 'PUT' }).then(() => {
      this.loadRules()
    }).catch(err => {
      wx.showToast({ title: '操作失败', icon: 'none' })
    })
  },

  addRule() {
    const formData = this.data.ruleType === 'storage' 
      ? { ruleName: '', productPattern: '', targetLocation: '', storageRatio: '1:1', priority: 100, isFinishedProduct: false, blankProductName: '', blankQuantityPerUnit: 1, isEnabled: true }
      : { productName: '', items: [{ componentName: '', quantity: 1 }], isEnabled: true }
    this.setData({ showModal: true, isEdit: false, currentId: null, formData, storageRatioIndex: 0 })
  },

  editRule(e) {
    const rule = e.currentTarget.dataset.rule
    let storageRatioIndex = 0
    if (this.data.ruleType === 'storage' && rule.storageRatio) {
      storageRatioIndex = this.data.storageRatios.indexOf(rule.storageRatio)
      if (storageRatioIndex === -1) storageRatioIndex = 0
    }
    const formData = this.data.ruleType === 'storage'
      ? { ...rule, isFinishedProduct: !!rule.isFinishedProduct, storageRatio: rule.storageRatio || '1:1' }
      : { ...rule, items: rule.items || [{ componentName: '', quantity: 1 }] }
    this.setData({ showModal: true, isEdit: true, currentId: rule.id, formData, storageRatioIndex })
  },

  closeModal() { this.setData({ showModal: false }) },
  preventClose() {},

  onInput(e) {
    const field = e.currentTarget.dataset.field
    let value = e.detail.value
    if (field === 'priority' || field === 'blankQuantityPerUnit') value = parseInt(value) || 0
    const updateData = {}
    updateData['formData.' + field] = value
    this.setData(updateData)
  },

  onSwitchChange(e) {
    const field = e.currentTarget.dataset.field
    const updateData = {}
    updateData['formData.' + field] = e.detail.value
    this.setData(updateData)
  },

  onRatioPicker(e) {
    const index = parseInt(e.detail.value)
    const ratio = this.data.storageRatios[index]
    this.setData({ 
      storageRatioIndex: index,
      'formData.storageRatio': ratio 
    })
  },

  onPartInput(e) {
    const idx = e.currentTarget.dataset.idx
    const field = e.currentTarget.dataset.field
    let value = e.detail.value
    if (field === 'quantity') value = parseInt(value) || 1
    const updateData = {}
    updateData['formData.items[' + idx + '].' + field] = value
    this.setData(updateData)
  },

  addPart() {
    const items = this.data.formData.items || []
    items.push({ componentName: '', quantity: 1 })
    this.setData({ 'formData.items': items })
  },

  deletePart(e) {
    const idx = e.currentTarget.dataset.idx
    const items = this.data.formData.items
    items.splice(idx, 1)
    this.setData({ 'formData.items': items })
  },

  saveRule() {
    const ruleType = this.data.ruleType
    const isEdit = this.data.isEdit
    const currentId = this.data.currentId
    const formData = this.data.formData
    const baseUrl = ruleType === 'storage' ? '/auto-storage-rules' : '/assembly-rules'
    const url = isEdit ? baseUrl + '/' + currentId : baseUrl
    const method = isEdit ? 'PUT' : 'POST'

    // 调试日志：检查发送的数据
    console.log('=== 保存规则数据 ===')
    console.log('storageRatio:', formData.storageRatio)
    console.log('完整formData:', JSON.stringify(formData))

    wx.showLoading({ title: '保存中...' })
    app.request({ url, method, data: formData }).then(() => {
      wx.showToast({ title: '保存成功', icon: 'success' })
      this.setData({ showModal: false })
      this.loadRules()
    }).catch(err => {
      console.error('保存失败', err)
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    }).finally(() => wx.hideLoading())
  },

  deleteRule() {
    wx.showModal({
      title: '确认删除',
      content: '删除后无法恢复，确定删除吗？',
      success: (res) => {
        if (res.confirm) {
          const ruleType = this.data.ruleType
          const currentId = this.data.currentId
          const url = ruleType === 'storage' ? '/auto-storage-rules/' + currentId : '/assembly-rules/' + currentId
          app.request({ url, method: 'DELETE' }).then(() => {
            wx.showToast({ title: '删除成功', icon: 'success' })
            this.setData({ showModal: false })
            this.loadRules()
          }).catch(err => {
            wx.showToast({ title: '删除失败', icon: 'none' })
          })
        }
      }
    })
  },

  // 重新应用规则到现有库存
  reapplyRules() {
    wx.showModal({
      title: '重新应用规则',
      content: '此操作将根据当前规则重新整理所有库存数据，可能需要一些时间。确定继续吗？',
      success: (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '应用中...', mask: true })
          app.request({ 
            url: '/auto-storage-rules/reapply', 
            method: 'POST' 
          }).then(res => {
            const result = res.data || {}
            wx.showModal({
              title: '应用完成',
              content: `成功处理 ${result.processedCount || 0} 条库存记录\n更新 ${result.updatedCount || 0} 条记录`,
              showCancel: false,
              confirmText: '知道了'
            })
          }).catch(err => {
            console.error('重新应用规则失败', err)
            wx.showModal({
              title: '应用失败',
              content: err.message || '规则应用失败，请检查后端服务是否正常',
              showCancel: false,
              confirmText: '知道了'
            })
          }).finally(() => {
            wx.hideLoading()
          })
        }
      }
    })
  }
})

