// utils/export-manager.js - 数据导出管理器
const Logger = require('./logger')

class ExportManager {
  /**
   * 复制文本到剪贴板
   */
  static copyToClipboard(text, successMessage = '已复制到剪贴板') {
    try {
      wx.setClipboardData({
        data: text,
        success() {
          wx.showToast({
            title: successMessage,
            icon: 'success'
          })
          Logger.info('Text copied to clipboard')
        },
        fail(error) {
          Logger.error('Failed to copy to clipboard', error)
          wx.showToast({
            title: '复制失败',
            icon: 'none'
          })
        }
      })
    } catch (error) {
      Logger.error('Copy to clipboard error', error)
    }
  }

  /**
   * 导出为CSV格式
   */
  static exportToCSV(data, filename = 'export.csv') {
    try {
      if (!Array.isArray(data) || data.length === 0) {
        wx.showToast({
          title: '没有数据可导出',
          icon: 'none'
        })
        return
      }

      // 获取表头
      const headers = Object.keys(data[0])
      
      // 构建CSV内容
      let csvContent = headers.join(',') + '\n'
      
      data.forEach(row => {
        const values = headers.map(header => {
          const value = row[header] || ''
          // 处理包含逗号的值
          return typeof value === 'string' && value.includes(',') 
            ? `"${value}"` 
            : value
        })
        csvContent += values.join(',') + '\n'
      })

      // 复制到剪贴板
      this.copyToClipboard(csvContent, 'CSV数据已复制到剪贴板')
      
      Logger.info('Data exported to CSV', { filename, rows: data.length })
    } catch (error) {
      Logger.error('CSV export failed', error)
      wx.showToast({
        title: '导出失败',
        icon: 'none'
      })
    }
  }

  /**
   * 导出为JSON格式
   */
  static exportToJSON(data, filename = 'export.json') {
    try {
      const jsonString = JSON.stringify(data, null, 2)
      this.copyToClipboard(jsonString, 'JSON数据已复制到剪贴板')
      
      Logger.info('Data exported to JSON', { filename, size: jsonString.length })
    } catch (error) {
      Logger.error('JSON export failed', error)
      wx.showToast({
        title: '导出失败',
        icon: 'none'
      })
    }
  }
}

module.exports = ExportManager