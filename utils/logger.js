// utils/logger.js - 统一日志管理
class Logger {
  static levels = {
    ERROR: 0,
    WARN: 1,
    INFO: 2,
    DEBUG: 3
  }

  static currentLevel = Logger.levels.INFO

  static log(level, message, data = null) {
    if (level > Logger.currentLevel) return

    const timestamp = new Date().toISOString()
    const levelName = Object.keys(Logger.levels)[level]
    const logMessage = `[${timestamp}] [${levelName}] ${message}`

    switch (level) {
      case Logger.levels.ERROR:
        console.error(logMessage, data)
        // 可以在这里添加错误上报逻辑
        break
      case Logger.levels.WARN:
        console.warn(logMessage, data)
        break
      case Logger.levels.INFO:
        console.log(logMessage, data)
        break
      case Logger.levels.DEBUG:
        console.log(logMessage, data)
        break
    }
  }

  static error(message, data) {
    Logger.log(Logger.levels.ERROR, message, data)
  }

  static warn(message, data) {
    Logger.log(Logger.levels.WARN, message, data)
  }

  static info(message, data) {
    Logger.log(Logger.levels.INFO, message, data)
  }

  static debug(message, data) {
    Logger.log(Logger.levels.DEBUG, message, data)
  }

  static setLevel(level) {
    Logger.currentLevel = level
  }
}

module.exports = Logger