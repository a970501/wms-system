# WMS 仓库管理系统

基于微信小程序和 Spring Boot 的现代化仓库管理解决方案。

## 项目简介

WMS（Warehouse Management System）仓库管理系统提供完整的库存管理、计件管理、工资统计、通知管理等功能，旨在提高仓库管理效率，减少人为错误，提升数据的准确性和实时性。

## 技术栈

### 前端（微信小程序）
- 微信小程序框架（基础库 2.10.0+）
- JavaScript ES6+
- WXML / WXSS

### 后端
- Spring Boot 2.7.18
- Spring Data JPA
- MySQL 8.0+
- Java 17
- JWT 身份认证

## 项目结构

```
├── app.js                 # 小程序入口文件
├── app.json               # 小程序全局配置
├── app.wxss               # 小程序全局样式
├── pages/                 # 页面目录
│   ├── index/             # 首页
│   ├── login/             # 登录页
│   ├── inventory/         # 库存管理
│   ├── piecework/         # 计件管理
│   ├── profile/           # 个人中心
│   └── ...
├── components/            # 组件目录
│   ├── advanced-search/   # 高级搜索组件
│   ├── chart/             # 图表组件
│   └── offline-status/    # 离线状态组件
├── utils/                 # 工具函数
├── images/                # 图片资源
├── wms-backend/           # 后端源码
│   ├── src/               # Java 源代码
│   ├── pom.xml            # Maven 配置
│   └── application.properties
└── 开发文档.md            # 详细开发文档
```

## 主要功能

- **库存管理**：商品入库、出库、库存查询、库存盘点
- **计件管理**：工人计件记录、计件统计、工资计算
- **工资统计**：按工人、产品、时间段统计工资
- **通知管理**：日志报告、邮件通知、微信通知
- **用户管理**：用户注册、登录、权限控制
- **数据导出**：Excel 导出功能

## 快速开始

### 前端（微信小程序）

1. 使用微信开发者工具打开项目根目录
2. 配置 `project.config.json` 中的 `appid`
3. 编译并预览

### 后端

1. 进入 `wms-backend` 目录
2. 配置 `application.properties` 中的数据库连接
3. 运行：
```bash
mvn clean package -DskipTests
java -jar target/wms-simple-1.0.0.jar
```

## 部署说明

详见 [README_部署说明.md](./README_部署说明.md)

## 开发文档

详见 [开发文档.md](./开发文档.md)

## License

MIT

