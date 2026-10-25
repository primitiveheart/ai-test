# diff-sdk

轻量 **JSON 差异对比 SDK**。输入两份 JSON，输出一组结构化的差异项（新增 / 删除 / 修改）。

- 零业务侵入：只依赖 `jackson-databind`
- 路径格式与前端 `JsonDiffViewer` 内部一致（点号路径，数组下标作为普通数字段）
- 支持通配符忽略路径、递归深度限制、数字宽松比较
- 核心类不可变、线程安全

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `diff-sdk-core` | 核心库：数据模型 + 递归对比实现 |
| `diff-sdk-script` | 命令行工具：`java -jar` 对比两个 JSON 文件 |

```
diff-sdk/
├── pom.xml                    # 聚合 POM（Java 8）
├── diff-sdk-core/
│   └── src/main/java/com/diff/sdk/
│       ├── DiffSdk.java            # 门面入口
│       ├── JsonDiffer.java         # 对比器接口
│       ├── JsonDiffers.java       # 对比器工厂
│       ├── DefaultJsonDiffer.java  # 默认递归实现
│       └── model/
│           ├── DiffItem.java      # 单条差异
│           ├── DiffResult.java    # 对比结果 + 序列化
│           ├── DiffType.java      # ADDED / DELETED / MODIFIED
│           ├── DiffConfig.java    # 对比配置（Builder）
│           ├── DiffValues.java    # 值的存在性判定与展示文本
│           └── PathPatterns.java  # 路径通配符匹配
└── diff-sdk-script/
```

## 安装

```bash
cd diff-sdk
mvn clean install
```

引入依赖：

```xml
<dependency>
    <groupId>com.diff</groupId>
    <artifactId>diff-sdk-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 快速开始

```java
DiffSdk sdk = new DiffSdk();

DiffResult result = sdk.compare(
        "{\"name\":\"Tom\",\"age\":18}",
        "{\"name\":\"Jerry\",\"age\":18}");

result.getPaths();         // ["name"]
result.getModifiedCount(); // 1
result.toJson();           // {"summary":{...},"items":[{"path":"name","type":"modified",...}]}
```

带配置：

```java
DiffConfig config = DiffConfig.builder()
        .ignorePaths("meta.**", "*.timestamp")   // 整棵子树不参与对比
        .maxDepth(20)                            // 超过则整棵子树按整体值比较
        .lenientNumbers(true)                    // 1 与 1.0 视为相等
        .build();

DiffResult result = new DiffSdk(config).compare(oldJson, newJson);
```

对比已解析的树或普通对象：

```java
sdk.compare(oldNode, newNode);        // JsonNode
sdk.compareObjects(oldPojo, newPojo); // POJO / Map / List
```

不想持有实例时：

```java
DiffResult result = DiffSdk.compareJson(oldJson, newJson, config);
```

## 对比语义

| 情况 | 类型 | oldValue | newValue |
| --- | --- | --- | --- |
| 路径仅在新值中存在 | `ADDED` | `null` | 新值 |
| 路径仅在旧值中存在 | `DELETED` | 旧值 | `null` |
| 两侧都有但值不同 | `MODIFIED` | 旧值 | 新值 |
| 一侧是容器、另一侧是标量 | `MODIFIED` | 整棵子树 | 整棵子树 |

- 容器（对象 / 数组）会继续下钻，差异记录到**叶子节点**；只有类型冲突或触及深度上限时，才把整棵子树当成一个值。
- `null` 与「字段缺失」等价，都视为不存在 —— 与前端判定口径一致。
- 差异项顺序稳定：对象按声明顺序，先旧值字段后新值字段；数组按下标。

## 路径与通配符

路径以 `.` 分隔，数组下标作为普通数字段：`items.0.name`。

`ignorePaths` 支持三种通配符：

| 通配符 | 含义 | 示例 |
| --- | --- | --- |
| `?` | 匹配单个字符，不跨 `.` | `item?.name` → `item0.name` |
| `*` | 匹配零或多个字符，不跨 `.` | `*.timestamp` → `user.timestamp` |
| `**` | 匹配零或多个完整路径段 | `meta.**` → `meta.trace.id` |

命中忽略的路径，其**整棵子树**都会先从两棵树里清洗掉再对比，因此不会产生任何差异项。根节点始终参与对比。

## 序列化

- `toMap()` —— 保留原始 JSON 结构（对象 / 数组保持为 `Map` / `List`），`type` 为小写类型名。
- `toViewMap()` —— 前端 `json-diff-view` 约定：`{status, summary:{added,deleted,modified,total}, diffResult:[{key,oldValue,newValue}]}`，其中 `oldValue` / `newValue` 一律是字符串（容器为紧凑 JSON 文本，缺失侧为 `null`）。

## 命令行工具

```bash
java -jar diff-sdk-script/target/diff-sdk-script-0.1.0-SNAPSHOT.jar old.json new.json
java -jar diff-sdk-script/target/diff-sdk-script-0.1.0-SNAPSHOT.jar \
     old.json new.json --ignore 'meta.**' --max-depth 5 --pretty
```

选项：`--ignore <pattern>`（可重复）、`--max-depth <n>`、`--strict-numbers`、`--pretty`、`-h/--help`。

退出码：`0` 无差异、`1` 有差异、`2` 用法或 IO 错误。

## 测试

```bash
cd diff-sdk
mvn test
```
