// components/offline-status/offline-status.js - 离线状态组件
const Logger = require('../../utils/logger')
const OfflineManager = require('../../utils/offline-manager')
const PlatformUtils = require('../../utils/platform-utils')

Component({
  properties: {
    // 是否显示详细信息
    showDetails: {
      type: Boolean,
      value: false
    },
    // 自定义样式
    customClass: {
      type: String,
      value: ''
    },
    // 位置：top, bottom, fixed
    position: {
      type: String,
      value: 'top'
    }
  },

  data: {
    isOnline: true,
    offlineQueueSize: 0,
    syncProgress: 0,
    isSyncing: false,
    lastSyncTime: null,
    networkType: 'unknown',
    showSyncDetails: false
  },

  lifetimes: {
    attached() {
      this.initNetworkMonitoring()
      this.updateOfflineStatus()
    },

    detached() {
      this.cleanup()
    }
  },

  methods: {
    /**
     * 初始化网络监控
     */
    initNetworkMonitoring() {
      // 监听网络状态变化
      const self = this
      wx.onNetworkStatusChange(function(res) {
        Logger.info('Network status changed in offline component', res)
        
        self.setData({
          isOnline: res.isConnected,
          networkType: res.networkType
        })

        if (res.isConnected) {
          self.onNetworkReconnected()
        } else {
          self.onNetworkDisconnected()
        }
      })

      // 获取初始网络状态
      this.checkNetworkStatus()
    },

    /**
     * 检查网络状态
     */
    async checkNetworkStatus() {
      try {
        const isOnline = await OfflineManager.checkNetworkStatus()
        const queueStatus = OfflineManager.getOfflineQueueStatus()
        
        // 获取网络类型
        const self = this
        wx.getNetworkType({
          success: function(res) {
            self.setData({
              isOnline: isOnline,
              networkType: res.networkType,
              offlineQueueSize: queueStatus.queueSize
            })
          }
        })
      } catch (error) {
        Logger.error('Failed to check network status', error)
      }
    },

    /**
     * 更新离线状态
     */
    updateOfflineStatus() {
      const queueStatus = OfflineManager.getOfflineQueueStatus()
      
      this.setData({
        offlineQueueSize: queueStatus.queueSize,
        lastSyncTime: queueStatus.newestOperation ? 
          new Date(queueStatus.newestOperation).toLocaleTimeString() : null
      })
    },

    /**
     * 网络重连处理
     */
    async onNetworkReconnected() {
      Logger.info('Network reconnected, starting sync')
      
      // 延迟一秒后开始同步，确保网络稳定
      const self = this
      setTimeout(function() {
        self.startSync()
      }, 1000)
    },

    /**
     * 网络断开处理
     */
    onNetworkDisconnected() {
      Logger.warn('Network disconnected')
      
      this.setData({
        isSyncing: false,
        syncProgress: 0
      })

      // 使用平台兼容的提示方式
      PlatformUtils.executePlatformSpecific({
        harmonyos: function() {
          // HarmonyOS可能需要特殊的提示方式
          wx.showToast({
            title: '网络已断开',
            icon: 'none',
            duration: 2000
          })
        },
        default: function() {
          wx.showToast({
            title: '网络已断开',
            icon: 'none',
            duration: 2000
          })
        }
      })
    },

    /**
     * 开始同步
     */
    async startSync() {
      if (this.data.isSyncing || !this.data.isOnline) {
        return
      }

      const queueSize = this.data.offlineQueueSize
      if (queueSize === 0) {
        return
      }

      this.setData({
        isSyncing: true,
        syncProgress: 0
      })

      try {
        // 模拟同步进度
        const progressInterval = setInterval(() => {
          const currentProgress = this.data.syncProgress
          if (currentProgress < 90) {
            this.setData({
              syncProgress: currentProgress + 10
            })
          }
        }, 200)

        // 执行实际同步
        await OfflineManager.syncOfflineData()

        // 清除进度定时器
        clearInterval(progressInterval)

        // 完成同步
        this.setData({
          syncProgress: 100,
          lastSyncTime: new Date().toLocaleTimeString()
        })

        // 延迟隐藏同步状态
        setTimeout(() => {
          this.setData({
            isSyncing: false,
            syncProgress: 0
          })
          this.updateOfflineStatus()
        }, 1000)

        // 显示成功提示
        wx.showToast({
          title: `已同步${queueSize}条数据`,
          icon: 'success'
        })

      } catch (error) {
        Logger.error('Sync failed', error)
        
        this.setData({
          isSyncing: false,
          syncProgress: 0
        })

        wx.showToast({
          title: '同步失败',
          icon: 'error'
        })
      }
    },

    /**
     * 手动同步
     */
    onManualSync() {
      if (!this.data.isOnline) {
        wx.showToast({
          title: '网络不可用',
          icon: 'none'
        })
        return
      }

      this.startSync()
    },

    /**
     * 切换详细信息显示
     */
    toggleSyncDetails() {
      this.setData({
        showSyncDetails: !this.data.showSyncDetails
      })
    },

    /**
     * 清空离线队列
     */
    onClearQueue() {
      wx.showModal({
        title: '确认清空',
        content: '确定要清空所有离线数据吗？此操作不可恢复。',
        success: (res) => {
          if (res.confirm) {
            OfflineManager.clearOfflineQueue()
            this.updateOfflineStatus()
            
            wx.showToast({
              title: '已清空离线队列',
              icon: 'success'
            })
          }
        }
      })
    },

    /**
     * 获取网络类型显示文本
     */
    getNetworkTypeText(networkType) {
      const typeMap = {
        'wifi': 'WiFi',
        '2g': '2G',
        '3g': '3G',
        '4g': '4G',
        '5g': '5G',
        'unknown': '未知',
        'none': '无网络'
      }
      return typeMap[networkType] || networkType
    },

    /**
     * 清理资源
     */
    cleanup() {
      // 清理定时器等资源
    }
  }
})