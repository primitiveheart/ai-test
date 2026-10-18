<template>
  <div class="json-compare-page">
    <a-card class="json-compare-card">
      <template #title>
        <div class="json-compare-title-container">
          <div class="json-compare-title">JSON对比工具</div>
          <a-button
            type="primary"
            :loading="loading"
            style="font-size: 14px; height: 32px"
            @click="handleJsonCompare"
          >
            <template #icon>
              <DiffOutlined />
            </template>
            JSON对比
          </a-button>
        </div>
      </template>

      <div class="example-bar">
        <span class="example-bar__label">示例数据</span>
        <a-select
          v-model:value="selectedExample"
          :options="exampleOptions"
          placeholder="选择示例快速填充"
          allow-clear
          style="width: 220px"
          @change="handleExampleChange"
        />
      </div>

      <div class="json-input-section">
        <div class="json-input-panel">
          <div class="json-input-panel__label">旧的JSON数据</div>
          <a-textarea
            v-model:value="formData.oldResult"
            :auto-size="{ minRows: 6, maxRows: 20 }"
            placeholder="请输入旧的JSON数据"
            allow-clear
          />
        </div>
        <div class="json-input-panel">
          <div class="json-input-panel__label">新的JSON数据</div>
          <a-textarea
            v-model:value="formData.newResult"
            :auto-size="{ minRows: 6, maxRows: 20 }"
            placeholder="请输入新的JSON数据"
            allow-clear
          />
        </div>
      </div>

      <!-- 结果展示区域 -->
      <div v-if="resultData.diffResult && resultData.diffResult.length > 0" class="result-section">
        <div class="result-header">
          <h3 class="result-title">JSON对比结果</h3>
          <a-checkbox v-model:checked="showIgnored"> 显示忽略的对比 </a-checkbox>
        </div>

        <!-- 差异结果表格 -->
        <div class="diff-table-wrapper">
          <a-table
            :data-source="resultData.diffResult"
            :columns="tableColumns"
            bordered
            :pagination="false"
            row-key="key"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'key'">
                <div class="key-cell">
                  <span class="key-link" @click="handleKeyClick(record.key)">{{
                    formatKeyForDisplay(record.key)
                  }}</span>
                  <a-button
                    type="text"
                    size="small"
                    class="copy-btn"
                    title="复制key"
                    @click.stop="handleCopyKey(record.key)"
                  >
                    <template #icon>
                      <CopyOutlined />
                    </template>
                  </a-button>
                </div>
              </template>
              <template v-else-if="column.key === 'newValue'">
                <span>{{ record.newValue }}</span>
              </template>
              <template v-else-if="column.key === 'oldValue'">
                <span>{{ record.oldValue }}</span>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="handleIgnoreField(record.key)">
                  {{ ignoredKeys.has(record.key) ? '取消忽略' : '忽略该字段属性对比' }}
                </a-button>
              </template>
            </template>
          </a-table>
        </div>

        <!-- JSON Diff 视图 -->
        <JsonDiffViewer
          ref="jsonDiffViewerRef"
          :old-json="oldJsonData"
          :new-json="newJsonData"
          :diff-map="diffMap"
          :ignored-keys="ignoredKeys"
          :show-ignored="showIgnored"
        />
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { CopyOutlined, DiffOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import JsonDiffViewer from './components/JsonDiffViewer.vue'

interface JsonCompareData {
  newResult: string
  oldResult: string
}

interface DiffRow {
  key: string
  oldValue: unknown
  newValue: unknown
}

interface JsonCompareResponse {
  status?: string
  exceptionMsg?: string
  diffResult: DiffRow[]
}

// 表单数据
const formData = reactive<JsonCompareData>({
  newResult: '{"hello": "newResultValue"}',
  oldResult: '{"hello": "oldResultValue"}',
})

// 示例数据（覆盖各种对比场景）
interface ExampleItem {
  label: string
  oldResult: string
  newResult: string
}

const examples: ExampleItem[] = [
  {
    label: '字段值修改',
    oldResult: JSON.stringify({ name: 'Tom', age: 18, city: 'Beijing' }, null, 2),
    newResult: JSON.stringify({ name: 'Tom', age: 19, city: 'Beijing' }, null, 2),
  },
  {
    label: '新增字段',
    oldResult: JSON.stringify({ name: 'Tom', age: 18 }, null, 2),
    newResult: JSON.stringify({ name: 'Tom', age: 18, email: 'tom@example.com' }, null, 2),
  },
  {
    label: '删除字段',
    oldResult: JSON.stringify({ name: 'Tom', age: 18, email: 'tom@example.com' }, null, 2),
    newResult: JSON.stringify({ name: 'Tom', age: 18 }, null, 2),
  },
  {
    label: '类型变化',
    oldResult: JSON.stringify({ id: '1001', count: '5', active: 'true' }, null, 2),
    newResult: JSON.stringify({ id: 1001, count: 5, active: true }, null, 2),
  },
  {
    label: '数组元素修改',
    oldResult: JSON.stringify({ tags: ['apple', 'banana', 'cherry'] }, null, 2),
    newResult: JSON.stringify({ tags: ['apple', 'banana', 'orange'] }, null, 2),
  },
  {
    label: '数组增删元素',
    oldResult: JSON.stringify({ items: [1, 2, 3] }, null, 2),
    newResult: JSON.stringify({ items: [1, 3, 4, 5] }, null, 2),
  },
  {
    label: '嵌套对象修改',
    oldResult: JSON.stringify(
      { user: { name: 'Tom', address: { city: 'Beijing', zip: '100000' } } },
      null,
      2,
    ),
    newResult: JSON.stringify(
      { user: { name: 'Tom', address: { city: 'Shanghai', zip: '200000' } } },
      null,
      2,
    ),
  },
  {
    label: 'null 值变化',
    oldResult: JSON.stringify({ name: 'Tom', nickname: null, remark: 'hello' }, null, 2),
    newResult: JSON.stringify({ name: 'Tom', nickname: 'Tommy', remark: null }, null, 2),
  },
  {
    label: '综合示例',
    oldResult: JSON.stringify(
      {
        id: 1,
        name: 'Tom',
        age: 18,
        tags: ['a', 'b'],
        address: { city: 'Beijing' },
        remark: 'to-be-removed',
      },
      null,
      2,
    ),
    newResult: JSON.stringify(
      {
        id: 1,
        name: 'Tom',
        age: '18',
        tags: ['a', 'c'],
        address: { city: 'Shanghai', zip: '200000' },
        added: 'new-field',
      },
      null,
      2,
    ),
  },
]

// 选中的示例索引
const selectedExample = ref<number | undefined>(undefined)

// 示例下拉选项
const exampleOptions = computed(() => examples.map((item, index) => ({ label: item.label, value: index })))

// 切换示例数据
const handleExampleChange = (value: number | undefined) => {
  if (value === undefined || value === null) {
    return
  }
  const example = examples[value]
  if (example) {
    formData.oldResult = example.oldResult
    formData.newResult = example.newResult
  }
}

// 结果数据
const resultData = reactive<JsonCompareResponse>({
  status: '',
  exceptionMsg: '',
  diffResult: [],
})

// 加载状态
const loading = ref(false)

// 忽略的字段集合
const ignoredKeys = reactive<Set<string>>(new Set())

// 是否显示已忽略的对比
const showIgnored = ref(false)

// JsonDiffViewer组件引用
const jsonDiffViewerRef = ref<InstanceType<typeof JsonDiffViewer> | null>(null)

// 表格列配置
const tableColumns: TableColumnsType<DiffRow> = [
  {
    title: 'key',
    key: 'key',
    dataIndex: 'key',
    width: 200,
  },
  {
    title: 'newValue',
    key: 'newValue',
    dataIndex: 'newValue',
    width: 250,
  },
  {
    title: 'oldValue',
    key: 'oldValue',
    dataIndex: 'oldValue',
    width: 250,
  },
  {
    title: '操作',
    key: 'action',
    width: 180,
  },
]

// 解析JSON数据（使用ref，只在点击对比按钮时更新）
const oldJsonData = ref<Record<string, unknown>>({})
const newJsonData = ref<Record<string, unknown>>({})

// 构建差异映射
const diffMap = computed(() => {
  const map = new Map<
    string,
    { type: 'added' | 'deleted' | 'modified'; oldValue?: unknown; newValue?: unknown }
  >()

  resultData.diffResult.forEach((diff) => {
    const key = diff.key

    if (diff.oldValue === null || diff.oldValue === undefined || diff.oldValue === 'null') {
      map.set(key, { type: 'added', newValue: diff.newValue })
    } else if (diff.newValue === null || diff.newValue === undefined || diff.newValue === 'null') {
      map.set(key, { type: 'deleted', oldValue: diff.oldValue })
    } else {
      map.set(key, { type: 'modified', oldValue: diff.oldValue, newValue: diff.newValue })
    }
  })

  return map
})

// 格式化key用于显示：将数组索引从点号格式转换为方括号格式，并添加$前缀
const formatKeyForDisplay = (key: string): string => {
  const formattedPath = key.replace(/\.(\d+)(?=\.|$)/g, '[$1]')
  return `$.${formattedPath}`
}

// 对象转字符串处理
const obj2Str = (obj: unknown): string | null => {
  if (obj === null) {
    return null
  }
  if (
    Object.prototype.toString.call(obj) === '[object Object]' ||
    Object.prototype.toString.call(obj) === '[object Array]'
  ) {
    return JSON.stringify(obj)
  }
  return String(obj)
}

// 忽略字段处理（支持切换忽略状态）
const handleIgnoreField = (key: string) => {
  if (ignoredKeys.has(key)) {
    ignoredKeys.delete(key)
  } else {
    ignoredKeys.add(key)
  }
}

// 点击key字段，定位到对应行
const handleKeyClick = (key: string) => {
  if (jsonDiffViewerRef.value) {
    jsonDiffViewerRef.value.scrollToKey(key)
  }
}

// 复制key值
const handleCopyKey = async (key: string) => {
  try {
    const formattedPath = key.replace(/\.(\d+)(?=\.|$)/g, '[$1]')
    const text = `$.${formattedPath}`

    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      message.success('已复制到剪贴板')
      return
    }

    const textArea = document.createElement('textarea')
    textArea.value = text
    textArea.style.position = 'fixed'
    textArea.style.opacity = '0'
    textArea.style.left = '-9999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()

    const success = document.execCommand('copy')
    if (success) {
      message.success('已复制到剪贴板')
    } else {
      message.error('复制失败')
    }
    document.body.removeChild(textArea)
  } catch (error) {
    console.error('复制失败:', error)
    message.error('复制失败')
  }
}

// 验证JSON是否合法
const validateJson = (jsonStr: string, fieldName: string): { valid: boolean; error?: string } => {
  if (!jsonStr || !jsonStr.trim()) {
    return { valid: true }
  }

  try {
    JSON.parse(jsonStr)
    return { valid: true }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    return {
      valid: false,
      error: `${fieldName}格式错误: ${errorMessage}`,
    }
  }
}

/** 本地 JSON 对比（原 mrp 页面依赖的后端接口占位，可在接入 API 时替换） */
async function jsonCompare(payload: JsonCompareData): Promise<JsonCompareResponse> {
  const { diffWithMeta } = await import('json-diff-toolkit')
  const oldVal = payload.oldResult?.trim() ? JSON.parse(payload.oldResult) : {}
  const newVal = payload.newResult?.trim() ? JSON.parse(payload.newResult) : {}
  const output = diffWithMeta(oldVal, newVal)

  const diffResult: DiffRow[] = output.results.map((item) => ({
    // 将 toolkit 的方括号路径（tags[2]）转为点号路径（tags.2），与 JsonDiffViewer 内部格式保持一致
    key: item.path.replace(/\[(\d+)\]/g, '.$1'),
    oldValue: item.oldValue,
    newValue: item.newValue,
  }))

  return {
    status: 'ok',
    diffResult,
  }
}

// JSON对比处理
const handleJsonCompare = async () => {
  if (!formData.newResult && !formData.oldResult) {
    message.warning('请至少输入一个JSON数据')
    return
  }

  if (formData.oldResult) {
    const oldValidation = validateJson(formData.oldResult, '旧的JSON数据')
    if (!oldValidation.valid) {
      message.error(oldValidation.error || '旧的JSON数据格式错误')
      return
    }
  }

  if (formData.newResult) {
    const newValidation = validateJson(formData.newResult, '新的JSON数据')
    if (!newValidation.valid) {
      message.error(newValidation.error || '新的JSON数据格式错误')
      return
    }
  }

  try {
    loading.value = true
    ignoredKeys.clear()

    try {
      oldJsonData.value = formData.oldResult ? JSON.parse(formData.oldResult) : {}
    } catch (error) {
      console.error('解析旧JSON失败:', error)
      oldJsonData.value = {}
    }

    try {
      newJsonData.value = formData.newResult ? JSON.parse(formData.newResult) : {}
    } catch (error) {
      console.error('解析新JSON失败:', error)
      newJsonData.value = {}
    }

    const response = await jsonCompare({
      newResult: formData.newResult,
      oldResult: formData.oldResult,
    })

    Object.assign(resultData, response)

    if (resultData.diffResult) {
      resultData.diffResult.forEach((diff) => {
        diff.oldValue = obj2Str(diff.oldValue)
        diff.newValue = obj2Str(diff.newValue)
      })
    }

    message.success('JSON对比完成')
  } catch (error) {
    console.error('JSON对比失败:', error)
    message.error('JSON对比失败:' + error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="less">
.json-compare-page {
  padding: 20px;

  .json-compare-card {
    .json-compare-title-container {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  }

  .example-bar {
    display: flex;
    align-items: center;
    gap: 12px;

    &__label {
      font-size: 14px;
      color: #606266;
      white-space: nowrap;
    }
  }

  .json-input-section {
    display: flex;
    gap: 16px;
    margin-top: 16px;

    .json-input-panel {
      flex: 1;
      min-width: 0;

      &__label {
        margin-bottom: 8px;
        font-size: 14px;
        font-weight: 500;
        color: #303133;
      }
    }
  }

  .result-section {
    margin-top: 20px;

    .result-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 0;

      .result-title {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: #303133;
      }
    }

    .diff-table-wrapper {
      margin: 20px 0;

      .key-cell {
        display: flex;
        align-items: center;
        gap: 8px;

        .key-link {
          color: #1677ff;
          cursor: pointer;
          text-decoration: none;
          transition: all 0.2s;
          flex: 1;

          &:hover {
            color: #4096ff;
            text-decoration: underline;
          }

          &:active {
            color: #0958d9;
          }
        }

        .copy-btn {
          flex-shrink: 0;
          padding: 4px;
          opacity: 0.6;
          transition: opacity 0.2s;

          &:hover {
            opacity: 1;
          }
        }
      }
    }
  }
}
</style>
