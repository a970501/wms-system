// pages/price-table/price-table.js
var app = getApp()
var Logger = require('../../utils/logger')

Page({
  data: {
    priceList: [],
    loading: false,
    showAddDialog: false,
    editingItem: null,
    formData: {
      productName: '',
      specification: '',
      material: '',
      connectionType: '',
      unitPrice: '',
      unit: '个'
    },
    materialOptions: ['304', '316', '碳钢', '铸铁', '铜', '其他'],
    materialIndex: 0,
    connectionTypeOptions: ['', 'BSPT', 'NPT', 'BSP', '其他'],
    connectionTypeIndex: 0
  },

  onLoad: function() {
    // 检查管理员权限
    var userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    if (userInfo.role !== 'ADMIN') {
      wx.showToast({ title: '仅管理员可访问', icon: 'none' })
      setTimeout(function() {
        wx.navigateBack()
      }, 1500)
      return
    }
    
    this.loadPriceList()
  },

  onShow: function() {
    // 每次显示时刷新数据
    this.loadPriceList()
  },

  loadPriceList: function() {
    var self = this
    this.setData({ loading: true })
    
    app.request({
      url: '/price-tables',
      method: 'GET'
    }).then(function(res) {
      var priceList = res.data || []
      self.setData({ 
        priceList: priceList,
        loading: false 
      })
      Logger.info('Price list loaded', { count: priceList.length })
    }).catch(function(err) {
      Logger.error('Failed to load price list', err)
      wx.showToast({ title: '加载失败', icon: 'none' })
      self.setData({ loading: false })
    })
  },

  showAddDialog: function() {
    this.setData({
      showAddDialog: true,
      editingItem: null,
      formData: {
        productName: '',
        specification: '',
        material: '',
        connectionType: '',
        unitPrice: '',
        unit: '个'
      },
      materialIndex: 0,
      connectionTypeIndex: 0
    })
  },

  showEditDialog: function(e) {
    var item = e.currentTarget.dataset.item
    var materialIndex = this.data.materialOptions.indexOf(item.material)
    var connectionTypeIndex = this.data.connectionTypeOptions.indexOf(item.connectionType || '')
    
    this.setData({
      showAddDialog: true,
      editingItem: item,
      formData: {
        productName: item.productName || '',
        specification: item.specification || '',
        material: item.material || '',
        connectionType: item.connectionType || '',
        unitPrice: item.unitPrice ? item.unitPrice.toString() : '',
        unit: item.unit || '个'
      },
      materialIndex: materialIndex >= 0 ? materialIndex : 0,
      connectionTypeIndex: connectionTypeIndex >= 0 ? connectionTypeIndex : 0
    })
  },

  hideAddDialog: function() {
    this.setData({ showAddDialog: false })
  },

  onFormInput: function(e) {
    var field = e.currentTarget.dataset.field
    var value = e.detail.value
    var updateData = {}
    updateData['formData.' + field] = value
    this.setData(updateData)
  },

  onMaterialChange: function(e) {
    var index = e.detail.value
    var material = this.data.materialOptions[index]
    this.setData({
      materialIndex: index,
      'formData.material': material
    })
  },

  onConnectionTypeChange: function(e) {
    var index = e.detail.value
    var connectionType = this.data.connectionTypeOptions[index]
    this.setData({
      connectionTypeIndex: index,
      'formData.connectionType': connectionType
    })
  },

  savePriceItem: function() {
    var self = this
    var formData = this.data.formData
    var editingItem = this.data.editingItem
    
    // 表单验证
    if (!formData.productName.trim()) {
      wx.showToast({ title: '请输入产品名称', icon: 'none' })
      return
    }
    if (!formData.specification.trim()) {
      wx.showToast({ title: '请输入规格', icon: 'none' })
      return
    }
    if (!formData.material.trim()) {
      wx.showToast({ title: '请选择材质', icon: 'none' })
      return
    }
    if (!formData.unitPrice.trim()) {
      wx.showToast({ title: '请输入单价', icon: 'none' })
      return
    }

    var unitPrice = parseFloat(formData.unitPrice)
    if (isNaN(unitPrice) || unitPrice <= 0) {
      wx.showToast({ title: '请输入有效的单价', icon: 'none' })
      return
    }

    wx.showLoading({ title: editingItem ? '修改中...' : '添加中...' })

    var requestData = {
      productName: formData.productName.trim(),
      specification: formData.specification.trim(),
      material: formData.material.trim(),
      connectionType: formData.connectionType.trim(),
      unitPrice: unitPrice,
      unit: formData.unit.trim()
    }

    var url = editingItem ? '/price-tables/' + editingItem.id : '/price-tables'
    var method = editingItem ? 'PUT' : 'POST'

    app.request({
      url: url,
      method: method,
      data: requestData
    }).then(function(res) {
      wx.hideLoading()
      wx.showToast({ 
        title: editingItem ? '修改成功' : '添加成功', 
        icon: 'success' 
      })
      self.hideAddDialog()
      self.loadPriceList()
      Logger.info('Price item saved', { isEdit: !!editingItem, data: requestData })
    }).catch(function(err) {
      wx.hideLoading()
      Logger.error('Failed to save price item', err)
      wx.showToast({ 
        title: err.message || (editingItem ? '修改失败' : '添加失败'), 
        icon: 'none' 
      })
    })
  },

  deletePriceItem: function(e) {
    var self = this
    var item = e.currentTarget.dataset.item
    
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个单价记录吗？',
      success: function(res) {
        if (res.confirm) {
          wx.showLoading({ title: '删除中...' })
          
          app.request({
            url: '/price-tables/' + item.id,
            method: 'DELETE'
          }).then(function() {
            wx.hideLoading()
            wx.showToast({ title: '删除成功', icon: 'success' })
            self.loadPriceList()
            Logger.info('Price item deleted', { id: item.id })
          }).catch(function(err) {
            wx.hideLoading()
            Logger.error('Failed to delete price item', err)
            wx.showToast({ title: '删除失败', icon: 'none' })
          })
        }
      }
    })
  },

  onPullDownRefresh: function() {
    this.loadPriceList()
    wx.stopPullDownRefresh()
  },

  exportPriceList: function() {
    var priceList = this.data.priceList
    if (priceList.length === 0) {
      wx.showToast({ title: '没有可导出的数据', icon: 'none' })
      return
    }

    var text = '单价表\n'
    text += '导出时间：' + new Date().toLocaleString() + '\n'
    text += '==================================================\n\n'
    
    // 表头
    text += '产品名称\t规格\t材质\t连接类型\t单价\t单位\n'
    text += '----------------------------------------------------------------------\n'
    
    // 数据行
    priceList.forEach(function(item) {
      text += item.productName + '\t' + item.specification + '\t' + item.material + '\t'
      text += (item.connectionType || '无') + '\t¥' + item.unitPrice + '\t' + (item.unit || '个') + '\n'
    })
    
    text += '\n' + '='.repeat(50) + '\n'
    text += '合计：' + priceList.length + '条记录'

    wx.setClipboardData({
      data: text,
      success: function() {
        wx.showToast({ title: '已复制到剪贴板', icon: 'success' })
      }
    })
  }
})