var app = getApp()

Page({
  data: {
    users: [],
    userLabels: [],
    userAIndex: -1,
    userBIndex: -1,
    userASelected: false,
    userBSelected: false,
    startDate: '',
    endDate: '',
    loading: false,
    resultTab: 'diff',
    diffTabClass: 'tab active',
    onlyATabClass: 'tab',
    onlyBTabClass: 'tab',
    statsTabClass: 'tab',
    showEmpty: false,
    onlyA: [],
    onlyB: [],
    diffs: [],
    productSummaryA: [],
    productSummaryB: [],
    summary: {
      onlyACount: 0,
      onlyBCount: 0,
      diffCount: 0
    }
  },

  onLoad() {
    if (!app.checkLogin()) return
    this.initDates()
    this.syncUiState()
  },

  onShow() {
    if (!app.checkLogin()) return
    this.loadUsers()
  },

  initDates() {
    const now = new Date()
    const year = now.getFullYear()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    const today = `${year}-${month}-${day}`
    const firstDay = `${year}-${month}-01`
    this.setData({ startDate: firstDay, endDate: today })
  },

  loadUsers() {
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {}
    if (userInfo.role !== 'ADMIN') {
      wx.showToast({ title: '仅管理员可对账', icon: 'none' })
      return
    }

    this.setData({ loading: true })

    app.request({ url: '/users' })
      .then(res => {
        const users = res.data || []
        const labels = users.map(u => u.username)
        const cur = (userInfo.username || '')
        const idx = labels.indexOf(cur)

        this.setData({
          users,
          userLabels: labels,
          userAIndex: idx >= 0 ? idx : (labels.length > 0 ? 0 : -1),
          userBIndex: labels.length > 1 ? (idx === 0 ? 1 : 0) : -1
        })

        this.syncUiState()
      })
      .catch(err => {
        console.error('加载用户失败', err)
        wx.showToast({ title: '加载用户失败', icon: 'none' })
      })
      .finally(() => {
        this.setData({ loading: false })
        this.syncUiState()
      })
  },

  onUserAChange(e) {
    this.setData({ userAIndex: Number(e.detail.value) })
    this.syncUiState()
  },

  onUserBChange(e) {
    this.setData({ userBIndex: Number(e.detail.value) })
    this.syncUiState()
  },

  onStartDateChange(e) {
    this.setData({ startDate: e.detail.value })
  },

  onEndDateChange(e) {
    this.setData({ endDate: e.detail.value })
  },

  setTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ resultTab: tab })
    this.syncUiState()
  },

  syncUiState() {
    const { userAIndex, userBIndex, resultTab, loading, diffs, onlyA, onlyB, productSummaryA, productSummaryB } = this.data

    const userASelected = userAIndex >= 0
    const userBSelected = userBIndex >= 0

    const diffTabClass = resultTab === 'diff' ? 'tab active' : 'tab'
    const onlyATabClass = resultTab === 'onlyA' ? 'tab active' : 'tab'
    const onlyBTabClass = resultTab === 'onlyB' ? 'tab active' : 'tab'
    const statsTabClass = resultTab === 'stats' ? 'tab active' : 'tab'

    let showEmpty = false
    if (!loading) {
      if (resultTab === 'diff') showEmpty = (diffs || []).length === 0
      if (resultTab === 'onlyA') showEmpty = (onlyA || []).length === 0
      if (resultTab === 'onlyB') showEmpty = (onlyB || []).length === 0
      if (resultTab === 'stats') showEmpty = (productSummaryA || []).length === 0 && (productSummaryB || []).length === 0
    }

    this.setData({
      userASelected,
      userBSelected,
      diffTabClass,
      onlyATabClass,
      onlyBTabClass,
      statsTabClass,
      showEmpty
    })
  },

  withDisplayFields(list) {
    return (list || []).map(it => {
      const connectionTypeDisplay = it.connectionType ? it.connectionType : '无'
      const unitPriceDisplay = (it.unitPrice === undefined || it.unitPrice === null || it.unitPrice === '')
        ? ''
        : String(it.unitPrice)
      const amountText = it.amtCents !== undefined
        ? (it.amtCents / 100).toFixed(2)
        : (it.amountText || '0.00')
      return { ...it, connectionTypeDisplay, unitPriceDisplay, amountText }
    })
  },

  moneyText(v) {
    return (parseFloat(v) || 0).toFixed(2)
  },

  formatDateOnly(v) {
    if (!v) return ''
    const d = new Date(v)
    if (isNaN(d.getTime())) return ''
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  },

  safeStr(v) {
    return (v === undefined || v === null) ? '' : String(v)
  },

  cents(v) {
    return Math.round((parseFloat(v) || 0) * 100)
  },

  makeKey(item) {
    const day = this.formatDateOnly(item.workDate || item.work_date || item.createdTime || item.created_time)
    const productName = this.safeStr(item.productName || item.product_name)
    const specification = this.safeStr(item.specification)
    const material = this.safeStr(item.material)
    const connectionType = this.safeStr(item.connectionType || item.connection_type)
    const unitPrice = this.safeStr(item.unitPrice || item.unit_price)
    return `${day}|${productName}|${specification}|${material}|${connectionType}|${unitPrice}`
  },

  aggregate(records) {
    const map = {}
    ;(records || []).forEach(r => {
      if (!r) return
      const key = this.makeKey(r)
      if (!map[key]) {
        map[key] = {
          key,
          day: this.formatDateOnly(r.workDate || r.work_date || r.createdTime || r.created_time),
          productName: this.safeStr(r.productName || r.product_name),
          specification: this.safeStr(r.specification),
          material: this.safeStr(r.material),
          connectionType: this.safeStr(r.connectionType || r.connection_type),
          unitPrice: this.safeStr(r.unitPrice || r.unit_price),
          qty: 0,
          amtCents: 0,
          rows: 0
        }
      }
      map[key].qty += parseInt(r.quantity) || 0
      map[key].amtCents += this.cents(r.totalAmount || r.total_amount)
      map[key].rows += 1
    })
    return map
  },

  reconcile() {
    const { userLabels, userAIndex, userBIndex } = this.data
    if (userAIndex < 0 || userBIndex < 0 || userAIndex === userBIndex) {
      wx.showToast({ title: '请选择两个不同用户', icon: 'none' })
      return
    }

    const userA = userLabels[userAIndex]
    const userB = userLabels[userBIndex]
    const { startDate, endDate } = this.data

    this.setData({
      loading: true,
      onlyA: [],
      onlyB: [],
      diffs: [],
      summary: { onlyACount: 0, onlyBCount: 0, diffCount: 0 },
      resultTab: 'diff'
    })
    this.syncUiState()

    const url = `/piecework/reconcile?userA=${encodeURIComponent(userA)}&userB=${encodeURIComponent(userB)}&startDate=${startDate}&endDate=${endDate}&includeUnitPrice=true&includeConnectionType=true`

    app.request({ url })
      .then(res => {
        const data = res.data || {}
        const diffsRaw = data.diffs || []
        const onlyARaw = data.onlyA || []
        const onlyBRaw = data.onlyB || []

        const diffs = diffsRaw.map(d => ({
          key: d.key,
          day: d.day,
          productName: d.productName,
          specification: d.specification,
          material: d.material,
          connectionType: d.connectionType,
          unitPrice: d.unitPrice,
          aQty: d.aQty,
          bQty: d.bQty,
          aAmt: this.moneyText(d.aAmt),
          bAmt: this.moneyText(d.bAmt),
          aRows: d.aRows,
          bRows: d.bRows,
          diffQty: d.diffQty,
          diffAmt: this.moneyText(d.diffAmt)
        }))

        const onlyA = onlyARaw.map(d => ({
          key: d.key,
          day: d.day,
          productName: d.productName,
          specification: d.specification,
          material: d.material,
          connectionType: d.connectionType,
          unitPrice: d.unitPrice,
          qty: d.qty,
          amtCents: this.cents(d.amount),
          rows: d.rows
        }))

        const onlyB = onlyBRaw.map(d => ({
          key: d.key,
          day: d.day,
          productName: d.productName,
          specification: d.specification,
          material: d.material,
          connectionType: d.connectionType,
          unitPrice: d.unitPrice,
          qty: d.qty,
          amtCents: this.cents(d.amount),
          rows: d.rows
        }))

        const onlyAView = this.withDisplayFields(onlyA)
        const onlyBView = this.withDisplayFields(onlyB)
        const diffsView = this.withDisplayFields(diffs)

        const summary = data.summary || {
          onlyACount: onlyAView.length,
          onlyBCount: onlyBView.length,
          diffCount: diffsView.length
        }

        // 处理产品统计数据
        const productSummaryA = (summary.productSummaryA || []).map(p => ({
          productName: p.productName,
          totalQty: p.totalQty,
          totalAmountText: this.moneyText(p.totalAmount)
        }))
        const productSummaryB = (summary.productSummaryB || []).map(p => ({
          productName: p.productName,
          totalQty: p.totalQty,
          totalAmountText: this.moneyText(p.totalAmount)
        }))

        this.setData({
          onlyA: onlyAView,
          onlyB: onlyBView,
          diffs: diffsView,
          summary,
          productSummaryA,
          productSummaryB
        })

        this.syncUiState()
      })
      .catch(err => {
        console.error('对账失败', err)
        wx.showToast({ title: '对账失败', icon: 'none' })
      })
      .finally(() => {
        this.setData({ loading: false })
        this.syncUiState()
      })
  },

  csvEscape(v) {
    const s = (v === undefined || v === null) ? '' : String(v)
    const needQuote = /[",\n\r\t]/.test(s)
    const out = s.replace(/"/g, '""')
    return needQuote ? `"${out}"` : out
  },

  sanitizeFileName(name) {
    return String(name || 'reconcile')
      .replace(/[\\/:*?"<>|]/g, '_')
      .replace(/\s+/g, '_')
  },

  buildCsv() {
    const { userLabels, userAIndex, userBIndex, startDate, endDate, diffs, onlyA, onlyB } = this.data
    const userA = userAIndex >= 0 ? userLabels[userAIndex] : ''
    const userB = userBIndex >= 0 ? userLabels[userBIndex] : ''

    const rows = []
    rows.push([`对账`, `${userA} vs ${userB}`, `日期`, `${startDate}~${endDate}`])
    rows.push([])

    const header = ['类型', '日期', '产品', '规格', '材质', '连接方式', '单价', 'A用户', 'A数量', 'A金额', 'B用户', 'B数量', 'B金额', '差异数量(A-B)', '差异金额(A-B)', 'A条数', 'B条数']
    rows.push(header)

    ;(diffs || []).forEach(d => {
      rows.push([
        '不一致',
        d.day,
        d.productName,
        d.specification,
        d.material,
        d.connectionTypeDisplay || d.connectionType || '无',
        d.unitPriceDisplay || d.unitPrice || '',
        userA,
        d.aQty,
        d.aAmt,
        userB,
        d.bQty,
        d.bAmt,
        d.diffQty,
        d.diffAmt,
        d.aRows,
        d.bRows
      ])
    })

    ;(onlyA || []).forEach(d => {
      rows.push([
        '仅A',
        d.day,
        d.productName,
        d.specification,
        d.material,
        d.connectionTypeDisplay || d.connectionType || '无',
        d.unitPriceDisplay || d.unitPrice || '',
        userA,
        d.qty,
        d.amountText || (d.amtCents !== undefined ? (d.amtCents / 100).toFixed(2) : ''),
        userB,
        '',
        '',
        '',
        '',
        d.rows,
        ''
      ])
    })

    ;(onlyB || []).forEach(d => {
      rows.push([
        '仅B',
        d.day,
        d.productName,
        d.specification,
        d.material,
        d.connectionTypeDisplay || d.connectionType || '无',
        d.unitPriceDisplay || d.unitPrice || '',
        userA,
        '',
        '',
        userB,
        d.qty,
        d.amountText || (d.amtCents !== undefined ? (d.amtCents / 100).toFixed(2) : ''),
        '',
        '',
        '',
        d.rows
      ])
    })

    // 添加产品统计
    const { productSummaryA, productSummaryB } = this.data
    rows.push([])
    rows.push(['=== 产品统计 ==='])
    rows.push([])
    rows.push([`用户A (${userA}) 产品统计`])
    rows.push(['产品名称', '总数量', '总金额'])
    ;(productSummaryA || []).forEach(p => {
      rows.push([p.productName, p.totalQty, p.totalAmountText])
    })
    rows.push([])
    rows.push([`用户B (${userB}) 产品统计`])
    rows.push(['产品名称', '总数量', '总金额'])
    ;(productSummaryB || []).forEach(p => {
      rows.push([p.productName, p.totalQty, p.totalAmountText])
    })

    const bom = '\ufeff'
    const csv = rows
      .map(r => r.map(c => this.csvEscape(c)).join(','))
      .join('\n')
    return { csv: bom + csv, userA, userB, startDate, endDate }
  },

  exportCsv() {
    const { diffs, onlyA, onlyB } = this.data
    if ((diffs || []).length === 0 && (onlyA || []).length === 0 && (onlyB || []).length === 0) {
      wx.showToast({ title: '暂无可导出数据', icon: 'none' })
      return
    }

    wx.showActionSheet({
      itemList: ['复制CSV到剪贴板', '保存CSV文件（本地）'],
      success: (res) => {
        const { csv, userA, userB, startDate, endDate } = this.buildCsv()
        if (res.tapIndex === 0) {
          wx.setClipboardData({
            data: csv,
            success: () => wx.showToast({ title: 'CSV已复制', icon: 'success' })
          })
          return
        }

        const fs = wx.getFileSystemManager()
        const base = `${this.sanitizeFileName(userA)}_vs_${this.sanitizeFileName(userB)}_${startDate}_${endDate}`
        const fileName = this.sanitizeFileName(`reconcile_${base}.csv`)
        const filePath = `${wx.env.USER_DATA_PATH}/${fileName}`

        fs.writeFile({
          filePath,
          data: csv,
          encoding: 'utf8',
          success: () => {
            wx.showModal({
              title: '导出成功',
              content: `已保存到本地文件：\n${filePath}\n\n可在开发者工具/真机文件管理中找到并导出。`,
              showCancel: false
            })
          },
          fail: (err) => {
            console.error('CSV写入失败', err)
            wx.showToast({ title: '保存失败，可改用复制', icon: 'none' })
          }
        })
      }
    })
  },

  copyResult() {
    const { userLabels, userAIndex, userBIndex, startDate, endDate, diffs, onlyA, onlyB } = this.data
    const userA = userAIndex >= 0 ? userLabels[userAIndex] : ''
    const userB = userBIndex >= 0 ? userLabels[userBIndex] : ''

    let text = `对账：${userA} vs ${userB}\n`
    text += `日期：${startDate} ~ ${endDate}\n`
    text += `不一致：${diffs.length}，仅A：${onlyA.length}，仅B：${onlyB.length}\n`
    text += `\n【不一致】\n`
    diffs.forEach(d => {
      text += `${d.day}\t${d.productName}\t${d.specification}\t${d.material}\t${d.connectionTypeDisplay || '无'}\tA:${d.aQty}/${d.aAmt}\tB:${d.bQty}/${d.bAmt}\n`
    })
    text += `\n【仅A】\n`
    onlyA.forEach(d => {
      text += `${d.day}\t${d.productName}\t${d.specification}\t${d.material}\t${d.connectionTypeDisplay || '无'}\t${d.qty}\t${d.amountText}\n`
    })
    text += `\n【仅B】\n`
    onlyB.forEach(d => {
      text += `${d.day}\t${d.productName}\t${d.specification}\t${d.material}\t${d.connectionTypeDisplay || '无'}\t${d.qty}\t${d.amountText}\n`
    })

    wx.setClipboardData({
      data: text,
      success: () => wx.showToast({ title: '已复制', icon: 'success' })
    })
  }
})
