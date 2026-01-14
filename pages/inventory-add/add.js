// pages/inventory-add/add.js
var app = getApp()

Page({
  data: {
    type: 'parts', // parts 或 blank
    isEdit: false, // 是否编辑模式
    editId: null,
    // 产品名称
    productNames: [],
    productNameIndex: -1,
    // 规格选项
    specOptions: ['DN15', 'DN20', 'DN25', 'DN32', 'DN40', 'DN50'],
    specIndex: -1,
    // 材质选项
    materials: ['304', '304重', '316', '316重'],
    materialIndex: -1,
    // 连接类型选项
    connectionTypes: ['', 'BSPT', 'NPT', 'BSP'],
    connectionTypeLabels: ['无', 'BSPT', 'NPT', 'BSP'],
    connectionTypeIndex: -1,
    // 表单
    formData: {
      productName: '',
      specification: '',
      material: '',
      quantity: 0,
      connectionType: '',
      unit: '个',
      remarks: ''
    },
    submitting: false
  },

  onLoad(options) {
    if (!app.checkLogin()) return
    const type = options.type || 'parts'
    const isEdit = options.edit === 'true'

    this.setData({ type, isEdit })
    this.loadProductNames()

    if (isEdit && options.data) {
      try {
        const item = JSON.parse(decodeURIComponent(options.data))
        this.setData({
          editId: options.id || item.id,
          formData: {
            productName: item.productName || '',
            specification: item.specification || '',
            material: item.material || '',
            quantity: item.quantity || 0,
            connectionType: item.connectionType || '',
            unit: item.unit || '个',
            remarks: item.remarks || ''
          },
          specIndex: this.data.specOptions.indexOf(item.specification),
          materialIndex: this.data.materials.indexOf(item.material),
          connectionTypeIndex: this.data.connectionTypes.indexOf(item.connectionType)
        })
        wx.setNavigationBarTitle({ title: '编辑库存' })
      } catch (e) {
        console.error('解析编辑数据失败', e)
      }
    } else {
      wx.setNavigationBarTitle({
        title: type === 'parts' ? '添加零件库存' : '添加毛坯库存'
      })
    }
  },

  // 从自动入库规则加载产品名称
  loadProductNames() {
    app.request({ url: '/auto-storage-rules' })
      .then(res => {
        const rules = res.data || []
        // 提取不重复的目标产品名称
        const names = [...new Set(rules.map(r => r.blankProductName || r.blank_product_name).filter(Boolean))].sort()
        this.setData({ productNames: names })

        // 编辑模式下设置选中索引
        if (this.data.isEdit && this.data.formData.productName) {
          const idx = names.indexOf(this.data.formData.productName)
          if (idx >= 0) this.setData({ productNameIndex: idx })
        }
      })
      .catch(err => {
        console.error('加载自动入库规则失败', err)
        const defaultNames = [
          '闸阀盖', '闸阀体', '闸板', '截止阀盖', '截止阀体',
          '过滤器体', '过滤器中头盖', '过滤器球阀阀体', '过滤器球阀盖',
          '水嘴帽', '水嘴球阀体', '水嘴球阀盖', '洗衣机接头', '皮管接头'
        ]
        this.setData({ productNames: defaultNames })
      })
  },

  // 产品名称选择
  onProductNameChange(e) {
    const index = e.detail.value
    this.setData({
      productNameIndex: index,
      'formData.productName': this.data.productNames[index]
    })
  },

  // 规格选择
  onSpecChange(e) {
    const index = e.detail.value
    this.setData({
      specIndex: index,
      'formData.specification': this.data.specOptions[index]
    })
  },

  // 材质选择
  onMaterialChange(e) {
    const index = e.detail.value
    this.setData({
      materialIndex: index,
      'formData.material': this.data.materials[index]
    })
  },

  // 连接类型选择
  onConnectionTypeChange(e) {
    const index = e.detail.value
    this.setData({
      connectionTypeIndex: index,
      'formData.connectionType': this.data.connectionTypes[index]
    })
  },

  // 数量减少
  decreaseQty() {
    let qty = parseInt(this.data.formData.quantity) || 0
    if (qty > 0) {
      this.setData({ 'formData.quantity': qty - 1 })
    }
  },

  // 数量增加
  increaseQty() {
    let qty = parseInt(this.data.formData.quantity) || 0
    this.setData({ 'formData.quantity': qty + 1 })
  },

  onQuantityInput(e) {
    let qty = parseInt(e.detail.value) || 0
    if (qty < 0) qty = 0
    this.setData({ 'formData.quantity': qty })
  },

  // 单位输入
  onUnitInput(e) {
    this.setData({ 'formData.unit': e.detail.value })
  },

  onRemarksInput(e) {
    this.setData({ 'formData.remarks': e.detail.value })
  },

  goBack() {
    wx.navigateBack()
  },

  submitForm() {
    const { type, formData, isEdit, editId } = this.data

    if (!formData.productName) {
      return wx.showToast({ title: '请选择产品名称', icon: 'none' })
    }
    if (!formData.specification) {
      return wx.showToast({ title: '请选择规格', icon: 'none' })
    }
    if (!formData.material) {
      return wx.showToast({ title: '请选择材质', icon: 'none' })
    }
    if (formData.quantity === '' || formData.quantity === undefined) {
      return wx.showToast({ title: '请输入数量', icon: 'none' })
    }

    this.setData({ submitting: true })
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}

    const postData = {
      productName: formData.productName,
      specification: formData.specification,
      material: formData.material,
      quantity: parseInt(formData.quantity),
      unit: formData.unit || '个',
      remarks: formData.remarks,
      workerName: userInfo.username
    }

    // 零件库存需要连接类型
    if (type === 'parts') {
      const isZhongTouGai = formData.productName && formData.productName.includes('中头盖')
      if (!isZhongTouGai && !formData.connectionType) {
        this.setData({ submitting: false })
        return wx.showToast({ title: '请选择连接类型', icon: 'none' })
      }
      if (formData.connectionType) {
        postData.connectionType = formData.connectionType
      }
    }

    let url = type === 'parts' ? '/inventory/items' : '/blank-inventory'
    let method = 'POST'

    if (isEdit && editId) {
      url = `${url}/${editId}`
      method = 'PUT'
    }

    app.request({ url, method, data: postData })
      .then(res => {
        wx.showToast({ title: isEdit ? '修改成功' : '添加成功', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 1500)
      })
      .catch(err => {
        console.error(isEdit ? '修改失败' : '添加失败', err)
        wx.showToast({ title: err.message || (isEdit ? '修改失败' : '添加失败'), icon: 'none' })
      })
      .finally(() => this.setData({ submitting: false }))
  }
})

