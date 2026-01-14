// components/advanced-search/advanced-search.js - 高级搜索组件
var Logger = require('../../utils/logger')
var StateManager = require('../../utils/state-manager')
var globalStateManager = StateManager.globalStateManager

Component({
  properties: {
    // 搜索字段配置
    fields: {
      type: Array,
      value: []
    },
    // 是否显示搜索面板
    visible: {
      type: Boolean,
      value: false
    },
    // 搜索历史记录数量
    historyLimit: {
      type: Number,
      value: 10
    }
  },

  data: {
    searchForm: {},
    searchHistory: [],
    hotSearches: [],
    showHistory: false
  },

  lifetimes: {
    attached: function() {
      this.loadSearchHistory()
      this.loadHotSearches()
    }
  },

  methods: {
    /**
     * 字段输入处理
     */
    onFieldInput: function(e) {
      var field = e.currentTarget.dataset.field
      var value = e.detail.value
      var updateData = {}
      updateData['searchForm.' + field] = value
      this.setData(updateData)
    },

    /**
     * 选择器变化处理
     */
    onPickerChange: function(e) {
      var field = e.currentTarget.dataset.field
      var value = e.detail.value
      var options = this.properties.fields.find(function(f) { return f.key === field }) || {}
      var optionsArray = options.options || []
      var selectedValue = optionsArray[value]
      
      var updateData = {}
      updateData['searchForm.' + field] = selectedValue
      updateData['searchForm.' + field + '_index'] = value
      this.setData(updateData)
    },

    /**
     * 日期变化处理
     */
    onDateChange: function(e) {
      var field = e.currentTarget.dataset.field
      var value = e.detail.value
      var updateData = {}
      updateData['searchForm.' + field] = value
      this.setData(updateData)
    },

    /**
     * 加载搜索历史
     */
    loadSearchHistory: function() {
      var history = globalStateManager.getState('searchHistory', [])
      this.setData({ searchHistory: history })
    },

    /**
     * 保存搜索历史
     */
    saveSearchHistory: function(searchParams) {
      var self = this
      var history = this.data.searchHistory
      var searchText = this.generateSearchText(searchParams)
      
      if (!searchText) return

      // 移除重复项
      var filteredHistory = history.filter(function(item) {
        return item.text !== searchText
      })
      
      // 添加到开头
      filteredHistory.unshift({
        text: searchText,
        params: searchParams,
        timestamp: Date.now()
      })

      // 限制数量
      var limitedHistory = filteredHistory.slice(0, this.properties.historyLimit)
      
      this.setData({ searchHistory: limitedHistory })
      globalStateManager.setState('searchHistory', limitedHistory)
      
      Logger.debug('Search history saved', { searchText: searchText, paramsCount: Object.keys(searchParams).length })
    },

    /**
     * 生成搜索文本描述
     */
    generateSearchText: function(params) {
      var self = this
      var descriptions = []
      
      Object.keys(params).forEach(function(key) {
        var value = params[key]
        if (value && value !== '') {
          var field = self.properties.fields.find(function(f) { return f.key === key })
          var label = field ? field.label : key
          descriptions.push(label + ':' + value)
        }
      })
      
      return descriptions.join(' ')
    },

    /**
     * 加载热门搜索
     */
    loadHotSearches: function() {
      // 这里可以从服务器获取热门搜索，暂时使用本地数据
      var hotSearches = [
        { text: '闸阀盖', type: 'product' },
        { text: 'DN20', type: 'spec' },
        { text: '304', type: 'material' },
        { text: '本月', type: 'time' }
      ]
      this.setData({ hotSearches: hotSearches })
    },

    /**
     * 字段值变化
     */
    onFieldChange: function(e) {
      var field = e.detail.field
      var value = e.detail.value
      var updateData = {}
      updateData['searchForm.' + field] = value
      this.setData(updateData)
    },

    /**
     * 执行搜索
     */
    onSearch: function() {
      var searchParams = this.data.searchForm
      
      // 过滤空值
      var filteredParams = {}
      Object.keys(searchParams).forEach(function(key) {
        var value = searchParams[key]
        if (value !== null && value !== undefined && value !== '') {
          filteredParams[key] = value
        }
      })

      if (Object.keys(filteredParams).length === 0) {
        wx.showToast({ title: '请输入搜索条件', icon: 'none' })
        return
      }

      // 保存搜索历史
      this.saveSearchHistory(filteredParams)

      // 触发搜索事件
      this.triggerEvent('search', { params: filteredParams })
      
      Logger.info('Advanced search executed', filteredParams)
    },

    /**
     * 重置搜索表单
     */
    onReset: function() {
      this.setData({ searchForm: {} })
      this.triggerEvent('reset')
      Logger.debug('Search form reset')
    },

    /**
     * 关闭搜索面板
     */
    onClose: function() {
      this.triggerEvent('close')
    },

    /**
     * 显示/隐藏搜索历史
     */
    toggleHistory: function() {
      this.setData({ showHistory: !this.data.showHistory })
    },

    /**
     * 使用历史搜索
     */
    useHistorySearch: function(e) {
      var index = e.currentTarget.dataset.index
      var historyItem = this.data.searchHistory[index]
      
      if (historyItem) {
        this.setData({ searchForm: historyItem.params })
        this.onSearch()
      }
    },

    /**
     * 删除历史记录
     */
    deleteHistory: function(e) {
      var index = e.currentTarget.dataset.index
      var history = this.data.searchHistory.slice() // 创建副本
      history.splice(index, 1)
      
      this.setData({ searchHistory: history })
      globalStateManager.setState('searchHistory', history)
      
      Logger.debug('Search history item deleted', { index: index })
    },

    /**
     * 清空搜索历史
     */
    clearHistory: function() {
      var self = this
      wx.showModal({
        title: '确认清空',
        content: '确定要清空所有搜索历史吗？',
        success: function(res) {
          if (res.confirm) {
            self.setData({ searchHistory: [] })
            globalStateManager.setState('searchHistory', [])
            Logger.info('Search history cleared')
          }
        }
      })
    },

    /**
     * 使用热门搜索
     */
    useHotSearch: function(e) {
      var text = e.currentTarget.dataset.text
      var type = e.currentTarget.dataset.type
      
      // 根据类型设置到对应字段
      var searchForm = Object.assign({}, this.data.searchForm)
      
      switch (type) {
        case 'product':
          searchForm.productName = text
          break
        case 'spec':
          searchForm.specification = text
          break
        case 'material':
          searchForm.material = text
          break
        default:
          // 设置到第一个文本字段
          var textField = this.properties.fields.find(function(f) { return f.type === 'input' })
          if (textField) {
            searchForm[textField.key] = text
          }
      }
      
      this.setData({ searchForm: searchForm })
      Logger.debug('Hot search used', { text: text, type: type })
    },

    /**
     * 快速搜索（语音输入等）
     */
    onQuickSearch: function() {
      // 这里可以集成语音搜索或其他快速搜索功能
      wx.showToast({ title: '语音搜索功能开发中', icon: 'none' })
    }
  },

  observers: {
    'visible': function(visible) {
      if (visible) {
        this.loadSearchHistory()
      }
    }
  }
})