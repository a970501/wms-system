// pages/assembly/assembly.js
var app = getApp()

Page({
  data: {
    // 规格选项
    specOptions: ['DN15', 'DN20', 'DN25', 'DN32', 'DN40', 'DN50'],
    specIndex: 0,
    // 材质选项
    materials: ['304', '304重', '316', '316重'],
    materialIndex: 0,
    // 连接类型选项
    connectionTypes: ['BSPT', 'NPT', 'BSP'],
    connectionTypeIndex: 0,
    // 组装规则数据
    assemblyRules: [],
    currentRule: null,
    productNames: [],
    productIndex: 0,
    // 所需零件列表
    requiredParts: [],
    // 库存检查
    inventoryStatus: [], // 每个零件的库存状态
    canAssemble: false,
    insufficientParts: [],
    // 表单
    formData: {
      productName: '',
      specification: '',
      material: '304',
      connectionType: 'BSPT',
      quantity: 1,
      remarks: ''
    },
    records: [],
    submitting: false
  },

  onLoad() {
    if (!app.checkLogin()) return
    this._inventoryCache = { items: null, fetchedAt: 0 }
    this._checkTimer = null
    this.loadAssemblyRules()
  },

  onShow() {
    this.loadRecords()
  },

  // 加载组装规则（包含所需零件）
  loadAssemblyRules() {
    app.request({ url: '/assembly-rules' })
      .then(res => {
        console.log('【Assembly】组装规则返回:', res)
        const rules = res.data || []
        this.setData({ assemblyRules: rules })
        // 提取产品名称
        const names = rules.map(r => r.productName).filter(Boolean)
        this.setData({
          productNames: names
        })
      })
      .catch(err => {
        console.log('加载组装规则失败', err)
        // 使用默认产品列表
        const defaultProducts = ['截止阀', '闸阀', '过滤器球阀', '洗衣机', '皮管', '排渣阀']
        this.setData({
          productNames: defaultProducts
        })
      })
  },

  loadRecords() {
    app.request({ url: '/assembly-records' })
      .then(res => {
        let records = res.data || []
        records = records.map(item => {
          let createdAtStr = ''
          if (item.createdAt) {
            const d = new Date(item.createdAt)
            createdAtStr = `${d.getMonth()+1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2,'0')}`
          }
          return { ...item, createdAtStr }
        })
        this.setData({ records })
      })
      .catch(err => console.error('加载组装记录失败', err))
  },

  // 产品选择
  onProductChange(e) {
    const index = e.detail.value
    const { productNames, assemblyRules } = this.data
    const name = productNames[index]
    // 找到对应的组装规则
    const rule = assemblyRules.find(r => r.productName === name)
    this.setData({
      productIndex: index,
      'formData.productName': name,
      'formData.specification': '',
      specIndex: 0,
      currentRule: rule,
      requiredParts: rule && rule.items ? rule.items : [],
      canAssemble: false,
      insufficientParts: []
    })
  },

  // 规格选择
  onSpecChange(e) {
    const index = e.detail.value
    const { specOptions } = this.data
    const spec = specOptions[index]
    this.setData({
      specIndex: index,
      'formData.specification': spec
    })
    // 选择规格后检查库存
    this.checkInventory()
  },

  onMaterialChange(e) {
    const index = e.detail.value
    this.setData({
      materialIndex: index,
      'formData.material': this.data.materials[index]
    })
    this.checkInventory()
  },

  onConnectionTypeChange(e) {
    const index = e.detail.value
    this.setData({
      connectionTypeIndex: index,
      'formData.connectionType': this.data.connectionTypes[index]
    })
    this.checkInventory()
  },

  // 数量减少
  decreaseQty() {
    let qty = parseInt(this.data.formData.quantity) || 1
    if (qty > 1) {
      this.setData({ 'formData.quantity': qty - 1 })
      this.checkInventory()
    }
  },

  // 数量增加
  increaseQty() {
    let qty = parseInt(this.data.formData.quantity) || 1
    this.setData({ 'formData.quantity': qty + 1 })
    this.checkInventory()
  },

  onQuantityInput(e) {
    let qty = parseInt(e.detail.value) || 1
    if (qty < 1) qty = 1
    this.setData({ 'formData.quantity': qty })
    this.checkInventory()
  },

  onRemarksInput(e) {
    this.setData({ 'formData.remarks': e.detail.value })
  },

  fetchInventoryItemsCached() {
    const cache = this._inventoryCache || { items: null, fetchedAt: 0 }
    const now = Date.now()
    if (cache.items && (now - cache.fetchedAt) < 10000) {
      return Promise.resolve(cache.items)
    }
    return app.request({ url: '/inventory/items' })
      .then(res => {
        const items = res.data || []
        this._inventoryCache = { items, fetchedAt: Date.now() }
        return items
      })
  },

  // 检查库存是否充足
  checkInventory() {
    const { formData, requiredParts } = this.data
    if (!formData.productName || !formData.specification || requiredParts.length === 0) {
      this.setData({ canAssemble: false, inventoryStatus: [], insufficientParts: [] })
      return
    }

    if (this._checkTimer) {
      clearTimeout(this._checkTimer)
    }
    this._checkTimer = setTimeout(() => {
      this.doCheckInventory()
    }, 200)
  },

  doCheckInventory() {
    const { formData, requiredParts } = this.data
    if (!formData.productName || !formData.specification || requiredParts.length === 0) {
      this.setData({ canAssemble: false, inventoryStatus: [], insufficientParts: [] })
      return
    }

    const qty = parseInt(formData.quantity) || 1
    const { specification, material, connectionType } = formData

    const { currentRule } = this.data
    if (currentRule && currentRule.id) {
      app.request({
        url: '/assembly-records/check',
        method: 'POST',
        data: {
          assemblyRuleId: currentRule.id,
          specification,
          material,
          connectionType,
          quantity: qty
        }
      })
        .then(res => {
          const data = res.data || {}
          const parts = data.parts || []
          const byName = {}
          parts.forEach(p => {
            if (p && p.componentName) byName[p.componentName] = p
          })

          const statusList = requiredParts.map(part => {
            const partName = part.componentName
            const unitQty = part.quantity || 1
            const s = byName[partName]
            const needed = s && s.required != null ? s.required : unitQty * qty
            const available = s && s.available != null ? s.available : 0
            const isEnough = s && s.sufficient != null ? !!s.sufficient : (available >= needed)
            return { componentName: partName, unitQty, needed, available, isEnough }
          })

          this.setData({
            inventoryStatus: statusList,
            canAssemble: data.canAssemble !== undefined ? !!data.canAssemble : statusList.every(i => i.isEnough),
            insufficientParts: data.insufficientParts || statusList.filter(i => !i.isEnough).map(i => i.componentName)
          })
        })
        .catch(() => {
          this.fetchInventoryItemsCached()
            .then(inventory => {
              const statusList = []
              const insufficient = []

              requiredParts.forEach(part => {
                const needed = (part.quantity || 1) * qty
                const partName = part.componentName
                const matchingItems = inventory.filter(inv => {
                  const nameMatch = inv.productName && (
                    inv.productName === partName ||
                    inv.productName.includes(partName) ||
                    partName.includes(inv.productName)
                  )
                  const specMatch = inv.specification === specification
                  const matMatch = inv.material === material
                  const ctMatch = !connectionType || !inv.connectionType || inv.connectionType === connectionType
                  return nameMatch && specMatch && matMatch && ctMatch
                })
                const available = matchingItems.reduce((sum, i) => sum + (i.quantity || 0), 0)
                const isEnough = available >= needed

                statusList.push({
                  componentName: partName,
                  unitQty: part.quantity || 1,
                  needed,
                  available,
                  isEnough
                })

                if (!isEnough) {
                  insufficient.push(partName)
                }
              })

              this.setData({
                inventoryStatus: statusList,
                canAssemble: insufficient.length === 0,
                insufficientParts: insufficient
              })
            })
            .catch(() => {
              this.setData({ canAssemble: false })
            })
        })

      return
    }

    this.fetchInventoryItemsCached()
      .then(inventory => {
        const statusList = []
        const insufficient = []

        requiredParts.forEach(part => {
          const needed = (part.quantity || 1) * qty
          // 查找匹配的库存（产品名+规格+材质）
          // 零件名称可能需要加上连接类型后缀
          const partName = part.componentName
          const matchingItems = inventory.filter(inv => {
            const nameMatch = inv.productName && (
              inv.productName === partName ||
              inv.productName.includes(partName) ||
              partName.includes(inv.productName)
            )
            const specMatch = inv.specification === specification
            const matMatch = inv.material === material
            const ctMatch = !connectionType || !inv.connectionType || inv.connectionType === connectionType
            return nameMatch && specMatch && matMatch && ctMatch
          })
          const available = matchingItems.reduce((sum, i) => sum + (i.quantity || 0), 0)
          const isEnough = available >= needed

          statusList.push({
            componentName: partName,
            unitQty: part.quantity || 1,
            needed,
            available,
            isEnough
          })

          if (!isEnough) {
            insufficient.push(partName)
          }
        })

        this.setData({
          inventoryStatus: statusList,
          canAssemble: insufficient.length === 0,
          insufficientParts: insufficient
        })
      })
      .catch(err => {
        console.error('检查库存失败', err)
        this.setData({ canAssemble: false })
      })
  },

  submitForm() {
    const { formData, canAssemble, insufficientParts } = this.data

    if (!formData.productName) {
      return wx.showToast({ title: '请选择装配产品', icon: 'none' })
    }
    if (!formData.specification) {
      return wx.showToast({ title: '请选择规格', icon: 'none' })
    }
    if (!formData.quantity || parseInt(formData.quantity) <= 0) {
      return wx.showToast({ title: '请输入有效数量', icon: 'none' })
    }

    // 如果库存不足，提示用户
    if (!canAssemble && insufficientParts.length > 0) {
      return wx.showModal({
        title: '库存不足',
        content: `以下零件库存不足：${insufficientParts.join('、')}，是否继续提交？`,
        success: (res) => {
          if (res.confirm) {
            this.doSubmit()
          }
        }
      })
    }

    this.doSubmit()
  },

  doSubmit() {
    const { formData, currentRule } = this.data
    this.setData({ submitting: true })
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}

    const postData = {
      assemblyRuleId: currentRule ? currentRule.id : null,
      productName: formData.productName,
      specification: formData.specification,
      material: formData.material,
      connectionType: formData.connectionType,
      quantity: parseInt(formData.quantity),
      remarks: formData.remarks,
      workerName: userInfo.username
    }

    app.request({ url: '/assembly-records', method: 'POST', data: postData })
      .then(res => {
        wx.showToast({ title: '组装成功', icon: 'success' })
        // 清空表单并重置状态
        this.setData({
          'formData.productName': '',
          'formData.specification': '',
          'formData.quantity': 1,
          'formData.remarks': '',
          currentRule: null,
          requiredParts: [],
          inventoryStatus: [],
          canAssemble: false,
          insufficientParts: []
        })
        // 刷新记录
        this.loadRecords()
      })
      .catch(err => {
        console.error('组装失败', err)
        wx.showToast({ title: err.message || '组装失败', icon: 'none' })
      })
      .finally(() => this.setData({ submitting: false }))
  }
})

