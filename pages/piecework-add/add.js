// pages/piecework-add/add.js
var app = getApp()

Page({
  data: {
    priceTables: [],
    productNames: [],
    specifications: [],
    // 材料选项
    materials: ['304', '304重', '316', '316重'],
    materialIndex: 0,
    // 连接类型选项
    connectionTypes: ['', 'BSPT', 'NPT', 'BSP'],
    connectionTypeLabels: ['无', 'BSPT', 'NPT', 'BSP'],
    connectionTypeIndex: 0,
    currentPrice: '',
    currentUnit: '个',
    formData: {
      productName: '',
      specification: '',
      material: '304',
      connectionType: '',
      quantity: '',
      semiFinished: false,
      isDefective: false,
      defectiveQuantity: '',
      defectiveReason: '',
      remarks: ''
    },
    submitting: false,
    isEditMode: false,
    recordId: null
  },

  tryInitEditSelectors() {
    if (!this.data.isEditMode) return
    const record = this._editRecord
    const tables = this.data.priceTables || []
    if (!record || !record.productName || tables.length === 0) return

    const specs = tables
      .filter(t => t.productName === record.productName)
      .map(t => t.specification)
      .filter(Boolean)
    const uniqueSpecs = [...new Set(specs)]

    if (uniqueSpecs.length > 0) {
      this.setData({ specifications: uniqueSpecs })
    }
  },

  onLoad(options) {
    if (!app.checkLogin()) return
    if (options.id) {
      // 编辑模式
      this.setData({ isEditMode: true, recordId: options.id })
      wx.setNavigationBarTitle({ title: '编辑计件记录' })
      this.loadRecord(options.id)
    }
    this.loadPriceTables()
  },

  loadPriceTables() {
    wx.showLoading({ title: '加载中...' })
    app.request({ url: '/price-tables' })
      .then(res => {
        const tables = res.data || []
        // 获取不重复的产品名称
        const productNames = [...new Set(tables.map(t => t.productName))]
        this.setData({ priceTables: tables, productNames })
        this.tryInitEditSelectors()
      })
      .catch(err => {
        console.error('加载价格表失败', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
      })
      .finally(() => wx.hideLoading())
  },

  onProductChange(e) {
    const index = e.detail.value
    const productName = this.data.productNames[index]
    // 筛选该产品的所有规格
    const specs = this.data.priceTables
      .filter(t => t.productName === productName)
      .map(t => t.specification)
    const uniqueSpecs = [...new Set(specs)]
    
    // 获取当前已选择的规格
    const currentSpec = this.data.formData.specification
    
    // 如果新产品也有当前规格，尝试查找价格
    let newPrice = ''
    let newUnit = '个'
    if (currentSpec && uniqueSpecs.includes(currentSpec)) {
      const priceItem = this.data.priceTables.find(
        t => t.productName === productName && t.specification === currentSpec
      )
      if (priceItem) {
        newPrice = priceItem.unitPrice
        newUnit = priceItem.unit || '个'
      }
    }
    
    this.setData({
      'formData.productName': productName,
      // 如果新产品没有当前规格，清空规格
      'formData.specification': (currentSpec && uniqueSpecs.includes(currentSpec)) ? currentSpec : '',
      specifications: uniqueSpecs,
      currentPrice: newPrice,
      currentUnit: newUnit
    })
  },

  onSpecChange(e) {
    const index = e.detail.value
    const spec = this.data.specifications[index]
    const { productName } = this.data.formData
    // 查找对应的价格
    const priceItem = this.data.priceTables.find(
      t => t.productName === productName && t.specification === spec
    )
    this.setData({
      'formData.specification': spec,
      currentPrice: priceItem ? priceItem.unitPrice : '',
      currentUnit: priceItem ? priceItem.unit : '个'
    })
  },

  onQuantityInput(e) {
    this.setData({ 'formData.quantity': e.detail.value })
  },

  // 材料选择
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

  onSemiFinishedChange(e) {
    this.setData({ 'formData.semiFinished': e.detail.value })
  },

  onDefectiveChange(e) {
    this.setData({ 'formData.isDefective': e.detail.value })
  },

  onDefectiveQuantityInput(e) {
    this.setData({ 'formData.defectiveQuantity': e.detail.value })
  },

  onDefectiveReasonInput(e) {
    this.setData({ 'formData.defectiveReason': e.detail.value })
  },

  onRemarksInput(e) {
    this.setData({ 'formData.remarks': e.detail.value })
  },

  loadRecord(id) {
    wx.showLoading({ title: '加载中...' })
    app.request({ url: `/piecework/${id}` })
      .then(res => {
        wx.hideLoading()
        const record = res.data
        if (record) {
          this._editRecord = record
          // 填充表单
          const materialIndex = this.data.materials.indexOf(record.material)
          const connectionTypeIndex = this.data.connectionTypes.indexOf(record.connectionType || '')
          this.setData({
            formData: {
              productName: record.productName,
              specification: record.specification,
              material: record.material,
              connectionType: record.connectionType || '',
              quantity: String(record.quantity),
              semiFinished: record.semiFinished === '是',
              isDefective: record.isDefective === 'true' || record.isDefective === true,
              defectiveQuantity: record.defectQuantity ? String(record.defectQuantity) : '',
              defectiveReason: record.defectiveReason || '',
              remarks: record.remarks || ''
            },
            materialIndex: materialIndex >= 0 ? materialIndex : 0,
            connectionTypeIndex: connectionTypeIndex >= 0 ? connectionTypeIndex : 0,
            currentPrice: record.unitPrice,
            currentUnit: record.unit || '个'
          })
          this.tryInitEditSelectors()
        }
      })
      .catch(err => {
        wx.hideLoading()
        console.error('加载记录失败', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 1500)
      })
  },

  submitForm() {
    const { formData, currentPrice, currentUnit } = this.data
    // 校验
    if (!formData.productName) {
      return wx.showToast({ title: '请选择产品', icon: 'none' })
    }
    if (!formData.specification) {
      return wx.showToast({ title: '请选择规格', icon: 'none' })
    }
    if (!formData.quantity || parseInt(formData.quantity) <= 0) {
      return wx.showToast({ title: '请输入有效数量', icon: 'none' })
    }
    // 如果是废品，报废数量必填
    if (formData.isDefective && (!formData.defectiveQuantity || parseInt(formData.defectiveQuantity) <= 0)) {
      return wx.showToast({ title: '请输入报废数量', icon: 'none' })
    }

    this.setData({ submitting: true })
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}

    const postData = {
      productName: formData.productName,
      specification: formData.specification,
      material: formData.material,
      connectionType: formData.connectionType,
      quantity: parseInt(formData.quantity),
      unitPrice: currentPrice,
      unit: currentUnit,
      workerName: userInfo.username,
      semiFinished: formData.semiFinished ? '是' : '',
      // 报废信息：isDefective 传 "true"/"false" 字符串，后端用 String 类型接收
      isDefective: formData.isDefective ? 'true' : 'false',
      // 注意：后端字段名是 defectQuantity（没有 ive）
      defectQuantity: formData.isDefective ? parseInt(formData.defectiveQuantity) : 0,
      defectiveReason: formData.defectiveReason || '',
      remarks: formData.remarks
    }

    const { isEditMode, recordId } = this.data
    const url = isEditMode ? `/piecework/${recordId}` : '/piecework'
    const method = isEditMode ? 'PUT' : 'POST'

    app.request({ url, method, data: postData })
      .then(res => {
        wx.showToast({ title: isEditMode ? '修改成功' : '添加成功', icon: 'success' })
        // 保存成功后返回上一页，详情页会在onShow时自动刷新
        setTimeout(() => wx.navigateBack(), 1500)
      })
      .catch(err => {
        console.error(isEditMode ? '修改失败' : '添加失败', err)
        wx.showToast({ title: err.message || (isEditMode ? '修改失败' : '添加失败'), icon: 'none' })
      })
      .finally(() => this.setData({ submitting: false }))
  }
})

