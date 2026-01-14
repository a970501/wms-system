// utils/platform-utils.js - 平台兼容性工具
const Logger = require('./logger')

class PlatformUtils {
  static platformInfo = null

  /**
   * 获取设备平台信息
   * @returns {Object} 平台信息
   */
  static getPlatformInfo() {
    if (PlatformUtils.platformInfo) {
      return PlatformUtils.platformInfo
    }

    try {
      // 使用新的 wx.getDeviceInfo API（基础库 3.7.0+）
      if (wx.getDeviceInfo) {
        const deviceInfo = wx.getDeviceInfo()
        const systemInfo = wx.getSystemInfoSync()
        
        PlatformUtils.platformInfo = {
          platform: deviceInfo.platform || systemInfo.platform,
          system: deviceInfo.system || systemInfo.system,
          version: deviceInfo.version || systemInfo.version,
          SDKVersion: systemInfo.SDKVersion,
          brand: deviceInfo.brand || systemInfo.brand,
          model: deviceInfo.model || systemInfo.model,
          isHarmonyOS: PlatformUtils.isHarmonyOS(deviceInfo, systemInfo),
          isIOS: (deviceInfo.platform || systemInfo.platform) === 'ios',
          isAndroid: (deviceInfo.platform || systemInfo.platform) === 'android',
          isDevtools: (deviceInfo.platform || systemInfo.platform) === 'devtools'
        }
      } else {
        // 兼容旧版本基础库
        const systemInfo = wx.getSystemInfoSync()
        
        PlatformUtils.platformInfo = {
          platform: systemInfo.platform,
          system: systemInfo.system,
          version: systemInfo.version,
          SDKVersion: systemInfo.SDKVersion,
          brand: systemInfo.brand,
          model: systemInfo.model,
          isHarmonyOS: PlatformUtils.isHarmonyOSLegacy(systemInfo),
          isIOS: systemInfo.platform === 'ios',
          isAndroid: systemInfo.platform === 'android',
          isDevtools: systemInfo.platform === 'devtools'
        }
      }

      Logger.info('Platform info detected', PlatformUtils.platformInfo)
      return PlatformUtils.platformInfo

    } catch (error) {
      Logger.error('Failed to get platform info', error)
      
      // 返回默认信息
      PlatformUtils.platformInfo = {
        platform: 'unknown',
        system: 'unknown',
        version: 'unknown',
        SDKVersion: 'unknown',
        brand: 'unknown',
        model: 'unknown',
        isHarmonyOS: false,
        isIOS: false,
        isAndroid: false,
        isDevtools: false
      }
      
      return PlatformUtils.platformInfo
    }
  }

  /**
   * 检测是否为HarmonyOS（新版本）
   * @param {Object} deviceInfo 设备信息
   * @param {Object} systemInfo 系统信息
   * @returns {boolean} 是否为HarmonyOS
   */
  static isHarmonyOS(deviceInfo, systemInfo) {
    const platform = deviceInfo.platform || systemInfo.platform
    const system = deviceInfo.system || systemInfo.system
    
    // 检查平台标识
    if (platform === 'harmonyos') {
      return true
    }
    
    // 检查系统信息中的HarmonyOS标识
    if (system && system.toLowerCase().includes('harmonyos')) {
      return true
    }
    
    return false
  }

  /**
   * 检测是否为HarmonyOS（兼容旧版本）
   * @param {Object} systemInfo 系统信息
   * @returns {boolean} 是否为HarmonyOS
   */
  static isHarmonyOSLegacy(systemInfo) {
    const { system, platform } = systemInfo
    
    // 检查系统信息
    if (system && system.toLowerCase().includes('harmonyos')) {
      return true
    }
    
    // 检查平台信息
    if (platform && platform.toLowerCase().includes('harmonyos')) {
      return true
    }
    
    return false
  }

  /**
   * 获取平台特定的配置
   * @returns {Object} 平台配置
   */
  static getPlatformConfig() {
    const platformInfo = PlatformUtils.getPlatformInfo()
    
    const config = {
      // 默认配置
      requestTimeout: 10000,
      maxConcurrentRequests: 6,
      cacheStrategy: 'adaptive',
      enableVibration: true,
      enableHapticFeedback: true,
      maxImageSize: 2 * 1024 * 1024, // 2MB
      supportedImageFormats: ['jpg', 'jpeg', 'png', 'gif']
    }

    // HarmonyOS特定优化
    if (platformInfo.isHarmonyOS) {
      config.requestTimeout = 12000 // HarmonyOS网络稍慢
      config.maxConcurrentRequests = 4 // 减少并发请求
      config.enableHapticFeedback = false // HarmonyOS暂不支持部分震动API
      config.cacheStrategy = 'conservative' // 更保守的缓存策略
      
      Logger.info('Applied HarmonyOS specific config')
    }

    // iOS特定优化
    if (platformInfo.isIOS) {
      config.enableHapticFeedback = true
      config.maxImageSize = 3 * 1024 * 1024 // iOS处理能力更强
      
      Logger.debug('Applied iOS specific config')
    }

    // Android特定优化
    if (platformInfo.isAndroid) {
      config.maxConcurrentRequests = 8 // Android并发能力较强
      config.enableVibration = true
      
      Logger.debug('Applied Android specific config')
    }

    // 开发工具特定配置
    if (platformInfo.isDevtools) {
      config.requestTimeout = 30000 // 开发环境超时时间更长
      config.enableVibration = false // 开发工具不支持震动
      config.enableHapticFeedback = false
      
      Logger.debug('Applied devtools specific config')
    }

    return config
  }

  /**
   * 执行平台特定的操作
   * @param {Object} actions 平台操作配置
   */
  static executePlatformSpecific(actions) {
    const platformInfo = PlatformUtils.getPlatformInfo()
    
    try {
      if (platformInfo.isHarmonyOS && actions.harmonyos) {
        Logger.debug('Executing HarmonyOS specific action')
        actions.harmonyos()
      } else if (platformInfo.isIOS && actions.ios) {
        Logger.debug('Executing iOS specific action')
        actions.ios()
      } else if (platformInfo.isAndroid && actions.android) {
        Logger.debug('Executing Android specific action')
        actions.android()
      } else if (platformInfo.isDevtools && actions.devtools) {
        Logger.debug('Executing devtools specific action')
        actions.devtools()
      } else if (actions.default) {
        Logger.debug('Executing default action')
        actions.default()
      }
    } catch (error) {
      Logger.error('Platform specific action failed', error)
      
      // 执行默认操作作为降级
      if (actions.default) {
        try {
          actions.default()
        } catch (fallbackError) {
          Logger.error('Default action also failed', fallbackError)
        }
      }
    }
  }

  /**
   * 获取平台兼容的API调用
   * @param {string} apiName API名称
   * @returns {Function|null} API函数
   */
  static getCompatibleAPI(apiName) {
    const platformInfo = PlatformUtils.getPlatformInfo()
    
    // API兼容性映射
    const apiCompatibility = {
      // 震动反馈API
      vibrateShort: {
        harmonyos: () => {
          // HarmonyOS可能不支持某些震动API
          if (wx.vibrateShort) {
            wx.vibrateShort({ type: 'light' })
          }
        },
        default: () => {
          if (wx.vibrateShort) {
            wx.vibrateShort({ type: 'medium' })
          }
        }
      },
      
      // 触觉反馈API
      hapticFeedback: {
        harmonyos: () => {
          // HarmonyOS暂时跳过触觉反馈
          Logger.debug('Haptic feedback skipped on HarmonyOS')
        },
        ios: () => {
          if (wx.vibrateShort) {
            wx.vibrateShort({ type: 'light' })
          }
        },
        default: () => {
          if (wx.vibrateShort) {
            wx.vibrateShort()
          }
        }
      }
    }

    const apiConfig = apiCompatibility[apiName]
    if (!apiConfig) {
      Logger.warn(`No compatibility config for API: ${apiName}`)
      return null
    }

    // 返回平台特定的API实现
    if (platformInfo.isHarmonyOS && apiConfig.harmonyos) {
      return apiConfig.harmonyos
    } else if (platformInfo.isIOS && apiConfig.ios) {
      return apiConfig.ios
    } else if (platformInfo.isAndroid && apiConfig.android) {
      return apiConfig.android
    } else {
      return apiConfig.default || null
    }
  }

  /**
   * 检查API是否支持
   * @param {string} apiName API名称
   * @returns {boolean} 是否支持
   */
  static isAPISupported(apiName) {
    try {
      return typeof wx[apiName] === 'function'
    } catch (error) {
      Logger.debug(`API ${apiName} not supported`, error)
      return false
    }
  }

  /**
   * 获取平台显示名称
   * @returns {string} 平台显示名称
   */
  static getPlatformDisplayName() {
    const platformInfo = PlatformUtils.getPlatformInfo()
    
    if (platformInfo.isHarmonyOS) {
      return 'HarmonyOS'
    } else if (platformInfo.isIOS) {
      return 'iOS'
    } else if (platformInfo.isAndroid) {
      return 'Android'
    } else if (platformInfo.isDevtools) {
      return '开发工具'
    } else {
      return '未知平台'
    }
  }

  /**
   * 重置平台信息缓存
   */
  static resetPlatformInfo() {
    PlatformUtils.platformInfo = null
    Logger.debug('Platform info cache reset')
  }
}

module.exports = PlatformUtils