# json-diff-view

面向开发、测试与运维的 **本地 JSON 对比** 单页应用：双栏输入、语义化 diff、左右分栏高亮与树形差异视图。数据仅在浏览器内解析，不上传服务器。

## 技术栈

- Vue 3 + TypeScript + Vite
- Ant Design Vue 4
- [json-diff-vue](https://www.npmjs.com/package/json-diff-vue) / json-diff-toolkit

## 脚本

```bash
npm install
npm run dev
npm run build
npm run preview
```

## 功能

- 旧 / 新 JSON 双栏编辑，支持上传 `.json` 文件
- 格式化、清空、加载示例
- 对比统计（新增 / 删除 / 修改）
- 分栏预览（行级高亮与 `+` / `-` / `~` 标记）
- 树形差异（`DiffViewer`）
