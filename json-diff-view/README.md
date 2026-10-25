# json-diff-view

面向开发、测试与运维的 **JSON 对比** 单页应用：双栏输入、语义化 diff、左右分栏高亮与树形差异视图。

差异计算由后端 [json-diff-server](../json-diff-server) 提供（底层是 [diff-sdk](../diff-sdk)）。

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

## 启动后端

前端的对比能力依赖后端服务，开发前先把后端跑起来：

```bash
# 1) 安装 diff-sdk 到本地仓库
cd diff-sdk && mvn clean install

# 2) 启动对比服务（默认 8080）
cd ../json-diff-server && mvn spring-boot:run
```

`vite.config.ts` 里已配置 `/api` → `http://localhost:8080` 的开发代理，前端代码统一使用相对路径 `/api/...` 调用，无需关心跨域。

## 功能

- 旧 / 新 JSON 双栏编辑，支持上传 `.json` 文件
- 格式化、清空、加载示例
- 对比统计（新增 / 删除 / 修改）
- 分栏预览（行级高亮与 `+` / `-` / `~` 标记）
- 树形差异（`DiffViewer`）
- 支持忽略指定字段的对比，并可切换是否显示被忽略的项

## 与后端的数据约定

请求 `POST /api/json-diff/compare`：

```json
{ "oldResult": "<json 文本>", "newResult": "<json 文本>", "ignorePaths": ["meta.**"] }
```

响应：

```json
{
  "status": "ok",
  "exceptionMsg": "",
  "summary": { "added": 1, "deleted": 0, "modified": 1, "total": 2 },
  "diffResult": [{ "key": "name", "oldValue": "Tom", "newValue": "Jerry" }]
}
```

`diffResult` 中 `oldValue` 缺失即为新增，`newValue` 缺失即为删除，两者都有即为修改。
