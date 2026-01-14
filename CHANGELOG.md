# 更新日志 (Changelog)

## [1.2.0] - 2025-01-15

### 🎨 UI/UX 样式优化

#### 全局样式优化 (app.wxss)
- **字体栈优化**：添加完整的字体回退链
  - SF Pro Text, SF Pro Display (iOS)
  - PingFang SC (macOS/iOS 中文)
  - Microsoft YaHei (Windows 中文)
  - Hiragino Sans GB, Source Han Sans CN (跨平台中文)
- **字体渲染优化**：
  - 添加 `-webkit-font-smoothing: antialiased` 抗锯齿
  - 添加 `-moz-osx-font-smoothing: grayscale` macOS 优化
  - 添加 `text-rendering: optimizeLegibility` 提升可读性
- **文字显示修复**：
  - 添加 `writing-mode: horizontal-tb` 防止文字竖排
  - 添加 `text-orientation: mixed` 确保混合文字正确显示
- **排版优化**：
  - 行高优化为 `line-height: 1.6`
  - 字间距微调 `letter-spacing: 0.02em`
- **数字显示优化**：
  - 添加 `font-variant-numeric: tabular-nums` 数字等宽对齐

#### 价格表页面优化 (price-table)
- **容器样式**：添加 `writing-mode` 防止文字竖排
- **标题样式**：增加 `letter-spacing` 提升可读性
- **按钮样式**：添加 `line-height: 1.4` 确保文字垂直居中
- **产品名称**：添加文字溢出处理 (`text-overflow: ellipsis`)
- **单价显示**：添加 `font-variant-numeric: tabular-nums` 数字对齐
- **详情标签**：改为 flex wrap 布局，添加背景色标签样式
- **输入框**：添加 focus 状态的 box-shadow 和 placeholder 颜色
- **对话框**：优化关闭按钮的 line-height
- **页面配置**：更新背景色配置与设计系统保持一致

### 🐛 Bug 修复
- 修复部分页面文字可能竖排显示的问题
- 修复数字显示不对齐的问题
- 修复输入框 focus 状态视觉反馈不明显的问题

### 📦 其他改进
- 统一设计系统颜色变量
- 优化页面背景渐变效果
- 提升整体视觉一致性

---

## [1.1.0] - 2025-01-14

### ✨ 新功能
- 添加单价表管理功能
- 添加数据导出功能
- 添加下拉刷新支持

### 🎨 UI 改进
- 全新 UI/UX Pro Max 设计系统
- 现代化渐变色彩方案
- 优化卡片阴影和圆角

---

## [1.0.0] - 2025-01-01

### 🎉 初始版本
- 计件工资管理
- 库存管理
- 用户权限管理
- 数据统计分析

