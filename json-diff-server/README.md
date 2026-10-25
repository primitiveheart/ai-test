# json-diff-server

基于 [diff-sdk](../diff-sdk) 的 **JSON 对比后端服务**，为前端 [json-diff-view](../json-diff-view) 提供对比接口。

- Spring Boot 2.7.11（JDK 8）
- 差异计算全部委托给 `diff-sdk-core`，本服务只负责参数校验与契约转换
- 统一响应信封，成功与失败结构一致

## 目录结构

```
json-diff-server/
├── pom.xml
└── src/
    ├── main/java/com/diff/server/
    │   ├── JsonDiffServerApplication.java
    │   ├── config/
    │   │   ├── DiffProperties.java        # diff.* 配置绑定
    │   │   ├── DiffConfigFactory.java     # 请求参数 → DiffConfig
    │   │   └── WebConfig.java             # CORS
    │   ├── controller/JsonDiffController.java
    │   ├── service/JsonDiffService.java
    │   ├── model/                         # DiffRequest / DiffResponse / DiffRow / DiffSummary
    │   └── exception/                     # InvalidJson / PayloadTooLarge / GlobalExceptionHandler
    └── main/resources/application.yml
```

## 前置条件

`diff-sdk-core` 需要先安装到本地仓库（它会随 `diff-sdk` 聚合工程一起安装）：

```bash
cd diff-sdk
mvn clean install
```

## 启动

```bash
cd json-diff-server
mvn spring-boot:run
# 或
mvn clean package && java -jar target/json-diff-server.jar
```

默认监听 `http://localhost:8080`。

## 接口

### `POST /api/json-diff/compare`

请求体：

```json
{
  "oldResult": "{\"name\":\"Tom\",\"age\":18}",
  "newResult": "{\"name\":\"Jerry\",\"age\":18,\"email\":\"j@example.com\"}",
  "ignorePaths": ["meta.**"],
  "maxDepth": 20,
  "lenientNumbers": true
}
```

`oldResult` / `newResult` 是 **JSON 文本字符串**（不是嵌套对象）；`ignorePaths` / `maxDepth` / `lenientNumbers` 可省略，省略时用服务端默认配置。

响应体：

```json
{
  "status": "ok",
  "exceptionMsg": "",
  "summary": { "added": 1, "deleted": 0, "modified": 1, "total": 2 },
  "diffResult": [
    { "key": "name", "oldValue": "Tom", "newValue": "Jerry" },
    { "key": "email", "newValue": "j@example.com" }
  ]
}
```

- `key`：差异路径，点号分隔（如 `items.0.name`）
- `oldValue` / `newValue`：一律字符串；容器值是紧凑 JSON 文本，缺失侧为 JSON `null`（配置为 `non_null`，字段会被省略）
- 失败时 `status` 为 `"error"`，`exceptionMsg` 带原因，`diffResult` 为空数组

示例：

```bash
curl -X POST http://localhost:8080/api/json-diff/compare \
  -H 'Content-Type: application/json' \
  -d '{"oldResult":"{\"a\":1}","newResult":"{\"a\":2}"}'
```

### `GET /api/json-diff/health`

```bash
curl http://localhost:8080/api/json-diff/health
# {"status":"UP"}
```

## 配置

`src/main/resources/application.yml`：

```yaml
server:
  port: 8080

diff:
  max-json-length: 5242880   # 单份 JSON 最大字符数，超出返回 413
  lenient-numbers: true      # 数字宽松比较：1 与 1.0 视为相等
  default-max-depth: 0       # 默认递归深度，非正数表示不限制
  default-ignore-paths: []   # 全局默认忽略路径
  cors:
    allowed-origins:
      - http://localhost:5173
```

请求里显式传 `ignorePaths` / `maxDepth` / `lenientNumbers` 会覆盖上面的默认值。

## 测试

```bash
cd json-diff-server
mvn test
```

覆盖：Web 层参数校验、Service 层对比逻辑、以及真实启动容器的端到端链路。
