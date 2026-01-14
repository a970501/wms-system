// components/chart/chart.js - 图表组件
var Logger = require('../../utils/logger')

Component({
  properties: {
    // 图表类型
    type: {
      type: String,
      value: 'line' // line, bar, pie, area
    },
    // 图表数据
    data: {
      type: Array,
      value: []
    },
    // 图表配置
    options: {
      type: Object,
      value: {}
    },
    // 宽度
    width: {
      type: String,
      value: '100%'
    },
    // 高度
    height: {
      type: String,
      value: '400rpx'
    },
    // 是否显示加载状态
    loading: {
      type: Boolean,
      value: false
    }
  },

  data: {
    canvasId: '',
    chartInstance: null
  },

  lifetimes: {
    attached: function() {
      this.setData({
        canvasId: 'chart_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
      })
    },

    ready: function() {
      this.initChart()
    },

    detached: function() {
      this.destroyChart()
    }
  },

  methods: {
    /**
     * 初始化图表
     */
    initChart: function() {
      // 这里可以集成第三方图表库，如 echarts-for-weixin
      // 或者使用 Canvas 2D API 绘制简单图表
      this.drawSimpleChart()
    },

    /**
     * 绘制简单图表 (使用Canvas 2D API)
     */
    drawSimpleChart: function() {
      var self = this
      var query = this.createSelectorQuery()
      query.select('#' + this.data.canvasId)
        .fields({ node: true, size: true })
        .exec(function(res) {
          if (!res[0]) return

          var canvas = res[0].node
          var ctx = canvas.getContext('2d')
          var dpr = wx.getSystemInfoSync().pixelRatio
          
          canvas.width = res[0].width * dpr
          canvas.height = res[0].height * dpr
          ctx.scale(dpr, dpr)

          self.renderChart(ctx, res[0].width, res[0].height)
        })
    },

    /**
     * 渲染图表
     */
    renderChart: function(ctx, width, height) {
      var type = this.properties.type
      var data = this.properties.data
      
      // 清空画布
      ctx.clearRect(0, 0, width, height)
      
      if (!data || data.length === 0) {
        this.drawEmptyState(ctx, width, height)
        return
      }

      switch (type) {
        case 'line':
          this.drawLineChart(ctx, width, height, data)
          break
        case 'bar':
          this.drawBarChart(ctx, width, height, data)
          break
        case 'pie':
          this.drawPieChart(ctx, width, height, data)
          break
        default:
          this.drawLineChart(ctx, width, height, data)
      }
    },

    /**
     * 绘制折线图
     */
    drawLineChart: function(ctx, width, height, data) {
      var padding = 40
      var chartWidth = width - padding * 2
      var chartHeight = height - padding * 2
      
      // 计算数据范围
      var values = data.map(function(item) { return item.value || 0 })
      var maxValue = Math.max.apply(Math, values)
      var minValue = Math.min.apply(Math, values)
      var valueRange = maxValue - minValue || 1

      // 绘制坐标轴
      ctx.strokeStyle = '#e0e0e0'
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(padding, padding)
      ctx.lineTo(padding, height - padding)
      ctx.lineTo(width - padding, height - padding)
      ctx.stroke()

      // 绘制数据线
      if (data.length > 1) {
        ctx.strokeStyle = '#409eff'
        ctx.lineWidth = 2
        ctx.beginPath()
        
        data.forEach(function(item, index) {
          var x = padding + (index / (data.length - 1)) * chartWidth
          var y = height - padding - ((item.value - minValue) / valueRange) * chartHeight
          
          if (index === 0) {
            ctx.moveTo(x, y)
          } else {
            ctx.lineTo(x, y)
          }
        })
        
        ctx.stroke()

        // 绘制数据点
        ctx.fillStyle = '#409eff'
        data.forEach(function(item, index) {
          var x = padding + (index / (data.length - 1)) * chartWidth
          var y = height - padding - ((item.value - minValue) / valueRange) * chartHeight
          
          ctx.beginPath()
          ctx.arc(x, y, 3, 0, 2 * Math.PI)
          ctx.fill()
        })
      }

      // 绘制标签
      ctx.fillStyle = '#666'
      ctx.font = '12px sans-serif'
      ctx.textAlign = 'center'
      
      data.forEach(function(item, index) {
        var x = padding + (index / (data.length - 1)) * chartWidth
        ctx.fillText(item.label || index, x, height - padding + 20)
      })
    },

    /**
     * 绘制柱状图
     */
    drawBarChart: function(ctx, width, height, data) {
      var padding = 40
      var chartWidth = width - padding * 2
      var chartHeight = height - padding * 2
      
      var maxValue = Math.max.apply(Math, data.map(function(item) { return item.value || 0 }))
      var barWidth = chartWidth / data.length * 0.6
      var barSpacing = chartWidth / data.length * 0.4

      // 绘制坐标轴
      ctx.strokeStyle = '#e0e0e0'
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(padding, padding)
      ctx.lineTo(padding, height - padding)
      ctx.lineTo(width - padding, height - padding)
      ctx.stroke()

      // 绘制柱子
      ctx.fillStyle = '#67c23a'
      data.forEach(function(item, index) {
        var barHeight = (item.value / maxValue) * chartHeight
        var x = padding + index * (barWidth + barSpacing) + barSpacing / 2
        var y = height - padding - barHeight
        
        ctx.fillRect(x, y, barWidth, barHeight)
        
        // 绘制数值
        ctx.fillStyle = '#333'
        ctx.font = '12px sans-serif'
        ctx.textAlign = 'center'
        ctx.fillText(item.value, x + barWidth / 2, y - 5)
        
        // 绘制标签
        ctx.fillText(item.label || index, x + barWidth / 2, height - padding + 20)
        
        ctx.fillStyle = '#67c23a'
      })
    },

    /**
     * 绘制饼图
     */
    drawPieChart: function(ctx, width, height, data) {
      var centerX = width / 2
      var centerY = height / 2
      var radius = Math.min(width, height) / 2 - 40
      
      var total = data.reduce(function(sum, item) { return sum + (item.value || 0) }, 0)
      var colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
      
      var currentAngle = -Math.PI / 2
      
      data.forEach(function(item, index) {
        var sliceAngle = (item.value / total) * 2 * Math.PI
        
        // 绘制扇形
        ctx.fillStyle = colors[index % colors.length]
        ctx.beginPath()
        ctx.moveTo(centerX, centerY)
        ctx.arc(centerX, centerY, radius, currentAngle, currentAngle + sliceAngle)
        ctx.closePath()
        ctx.fill()
        
        // 绘制标签
        var labelAngle = currentAngle + sliceAngle / 2
        var labelX = centerX + Math.cos(labelAngle) * (radius * 0.7)
        var labelY = centerY + Math.sin(labelAngle) * (radius * 0.7)
        
        ctx.fillStyle = '#fff'
        ctx.font = '12px sans-serif'
        ctx.textAlign = 'center'
        ctx.fillText(item.label || index, labelX, labelY)
        
        currentAngle += sliceAngle
      })
    },

    /**
     * 绘制空状态
     */
    drawEmptyState: function(ctx, width, height) {
      ctx.fillStyle = '#c0c4cc'
      ctx.font = '16px sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText('暂无数据', width / 2, height / 2)
    },

    /**
     * 销毁图表
     */
    destroyChart: function() {
      if (this.data.chartInstance) {
        // 清理图表实例
        this.data.chartInstance = null
      }
    },

    /**
     * 更新图表数据
     */
    updateChart: function() {
      this.initChart()
    },

    /**
     * 导出图表为图片
     */
    exportImage: function() {
      var self = this
      var query = this.createSelectorQuery()
      query.select('#' + this.data.canvasId)
        .fields({ node: true })
        .exec(function(res) {
          if (!res[0]) return

          var canvas = res[0].node
          wx.canvasToTempFilePath({
            canvas: canvas,
            success: function(result) {
              self.triggerEvent('export', { 
                tempFilePath: result.tempFilePath 
              })
              Logger.info('Chart exported as image', { path: result.tempFilePath })
            },
            fail: function(error) {
              Logger.error('Failed to export chart', error)
            }
          })
        })
    }
  },

  observers: {
    'data, type': function() {
      if (this.data.canvasId) {
        this.updateChart()
      }
    }
  }
})