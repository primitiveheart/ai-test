<template>
    <div class="json-diff-viewer-wrapper">
        <!-- 左侧面板 -->
        <div class="json-diff-panel">
            <div class="json-diff-panel__header">
                <span class="json-diff-panel__title">{{ oldTitle }}</span>
                <a-button type="text" size="small" class="json-diff-panel__copy-btn" title="复制"
                    @click="handleCopy(oldJson, oldTitle)">
                    <template #icon>
                        <CopyOutlined />
                    </template>
                </a-button>
            </div>
            <div class="json-diff-panel-content" ref="leftContentRef">
                <div v-for="(line, lineIndex) in mergedLines" :key="lineIndex" :class="[
                    getLineClass(line.oldLine, 'old'),
                    {
                        'highlight-line': highlightedLineIndex === lineIndex,
                        'highlight-added': highlightedLineIndex === lineIndex && highlightedLineIndexType === 'added',
                        'highlight-deleted': highlightedLineIndex === lineIndex && highlightedLineIndexType === 'deleted',
                        'highlight-modified': highlightedLineIndex === lineIndex && highlightedLineIndexType === 'modified',
                        'highlight-normal': highlightedLineIndex === lineIndex && highlightedLineIndexType === 'normal',
                    }
                ]" class="json-line">
                    <span class="line-number">{{ line.oldLine ? line.oldLineNumber : '' }}</span>
                    <span v-if="line.oldLine?.foldable" class="fold-icon" :title="foldedPaths.has(getFoldablePath(line.oldLine)) ? '展开' : '收起'"
                        @click="toggleFold(getFoldablePath(line.oldLine))">
                        <CaretRightFilled v-if="foldedPaths.has(getFoldablePath(line.oldLine))" />
                        <CaretDownFilled v-else />
                    </span>
                    <span v-else class="fold-placeholder"></span>
                    <span v-if="line.oldLine?.keyName && line.oldLine?.path" class="copy-icon"
                        @click="handleCopyPath(line.oldLine.path)" title="复制JsonPath">
                        <CopyOutlined />
                    </span>
                    <span v-else class="copy-placeholder"></span>
                    <span class="line-content" v-html="line.oldLine ? getHighlightedContent(line.oldLine) : ''"
                        :class="{ 'empty-line': !line.oldLine }"></span>

                </div>
            </div>
        </div>
        <!-- 右侧面板 -->
        <div class="json-diff-panel">
            <div class="json-diff-panel__header">
                <span class="json-diff-panel__title">{{ newTitle }}</span>
                <a-button type="text" size="small" class="json-diff-panel__copy-btn" title="复制"
                    @click="handleCopy(newJson, newTitle)">
                    <template #icon>
                        <CopyOutlined />
                    </template>
                </a-button>
            </div>
            <div class="json-diff-panel-content" ref="rightContentRef">
                <div v-for="(line, lineIndex) in mergedLines" :key="lineIndex" :class="[
                    getLineClass(line.newLine, 'new'),
                ]" class="json-line">
                    <span class="line-number">{{ line.newLine ? line.newLineNumber : '' }}</span>
                    <span v-if="line.newLine?.foldable" class="fold-icon" :title="foldedPaths.has(getFoldablePath(line.newLine)) ? '展开' : '收起'"
                        @click="toggleFold(getFoldablePath(line.newLine))">
                        <CaretRightFilled v-if="foldedPaths.has(getFoldablePath(line.newLine))" />
                        <CaretDownFilled v-else />
                    </span>
                    <span v-else class="fold-placeholder"></span>
                    <span v-if="line.newLine?.keyName && line.newLine?.path" class="copy-icon"
                        @click="handleCopyPath(line.newLine.path)" title="复制JsonPath">
                        <CopyOutlined />
                    </span>
                    <span v-else class="copy-placeholder"></span>
                    <span class="line-content" v-html="line.newLine ? getHighlightedContent(line.newLine) : ''"
                        :class="{ 'empty-line': !line.newLine }"></span>

                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { CopyOutlined, CaretDownFilled, CaretRightFilled } from '@ant-design/icons-vue';
import { ref, computed, nextTick, onMounted } from 'vue';
import { message } from 'ant-design-vue';



interface JsonLine {
    content: string;
    path: string;
    foldable: boolean;
    level: number;
    type: 'key' | 'value' | 'bracket';
    keyName?: string;
    lineIndex: number;
    endLineIndex?: number; // 对于可折叠的行，记录结束括号的行索引
}

interface MergedLine {
    oldLine: JsonLine | null;
    newLine: JsonLine | null;
    oldLineNumber: number;
    newLineNumber: number;
}

type DiffType = 'added' | 'deleted' | 'modified';

interface Props {
    oldJson: Record<string, unknown>;
    newJson: Record<string, unknown>;
    diffMap: Map<string, {type: DiffType; oldValue?: unknown; newValue?: unknown}>;
    ignoredKeys: Set<string>;
    showIgnored: boolean;
    oldTitle?: string;
    newTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
    oldTitle: '旧JSON数据',
    newTitle: '新JSON数据'
})

//折叠状态
const foldedPaths = ref<Set<string>>(new Set());

// 内容区域引用(用于同步滚动)
const leftContentRef = ref<HTMLElement | null>(null);
const rightContentRef = ref<HTMLElement | null>(null);

// 当前高亮的行索引（用于动画）
const highlightedLineIndex = ref<number | null>(null);

// 当前高亮的行类型（用于设置颜色）
const highlightedLineIndexType = ref<DiffType | 'normal' | null>(null);

// 是否正在程序化滚动（用于临时禁用同步滚动）
const isProgrammaticScrolling = ref(false);

// 解析JSON为行数组，正确处理数组索引路径和折叠范围
const parseJsonToLines = (json: Record<string, unknown>): JsonLine[] => {
  const lines: JsonLine[] = [];
  const jsonStr = JSON.stringify(json, null, 2);
  let jsonLines = jsonStr.split('\n');
  
  // 【前置处理】将空对象 {} 拆分为两行显示：{ 和 }
  // 在解析之前就处理，确保后续逻辑统一处理
  const processedLines: string[] = [];
  jsonLines.forEach((line) => {
    const trimmedLine = line.trim();
    // 匹配 "key": {} 或 "key": {}, 的模式
    const emptyObjectMatch = trimmedLine.match(/^("([^"]+)":\s*)\{\}(\s*,)?$/);
    
    if (emptyObjectMatch) {
      // 提取缩进
      const indent = line.search(/\S/);
      const indentStr = indent >= 0 ? line.substring(0, indent) : '';
      const keyPart = emptyObjectMatch[1]; // "key": 
      const commaIndex = 3;
      const comma = emptyObjectMatch[commaIndex] || ''; // 逗号（如果有）
      
      // 第一行：key: {
      const firstLine = indentStr + keyPart + '{';
      // 第二行：} 或 },
      const secondLine = indentStr + '}' + comma;
      
      processedLines.push(firstLine);
      processedLines.push(secondLine);
    } else {
      // 非空对象行，直接添加
      processedLines.push(line);
    }
  });
  jsonLines = processedLines;
  
  interface StackItem {
    key: string;
    isArray: boolean;
    arrayIndex: number;
    startLineIndex: number; // 记录开始行
    level: number; // 记录层级
  }
  
  const pathStack: StackItem[] = [];
  const foldableStack: number[] = []; // 记录可折叠行的索引
  
  jsonLines.forEach((line, index) => {
    const trimmedLine = line.trim();
    const indent = line.search(/\S/);
    const level = Math.floor(indent / 2);
    
    // 检测行类型
    const keyMatch = trimmedLine.match(/"([^"]+)":\s*(.*)$/);
    const keyName = keyMatch ? keyMatch[1] : '';
    const valueStart = keyMatch ? keyMatch[2] : '';
    
    const isArrayStart = valueStart.startsWith('[');
    const isObjectStart = valueStart.startsWith('{');
    const isObjectInArray = trimmedLine === '{' || trimmedLine === '{,';
    const isArrayInArray = trimmedLine === '[' || trimmedLine === '[,'; // 数组中的数组元素
    const isCloseBracket = trimmedLine === '}' || trimmedLine === '},' || trimmedLine === ']' || trimmedLine === '],';
    
    // 检测空数组/对象（开始和结束在同一行）
    const isEmptyArray = keyName && (valueStart === '[]' || valueStart === '[],');
    const isEmptyObject = keyName && (valueStart === '{}' || valueStart === '{},');
    const isEmptyBracket = isEmptyArray || isEmptyObject;
    
    // 如果是结束括号，关联到对应的开始行，并继承path
    let closeBracketPath = '';
    if (isCloseBracket && foldableStack.length > 0 && !isEmptyBracket) {
      // 空数组/对象不需要从栈中pop，因为开始和结束在同一行
      const startIndex = foldableStack.pop()!;
      if (lines[startIndex]) {
        lines[startIndex].endLineIndex = index;
        // 结束括号继承开始行的path
        closeBracketPath = lines[startIndex].path;
      }
    }
    
    // 调整栈深度到当前层级
    while (pathStack.length > level) {
      pathStack.pop();
    }
    
    // 构建当前路径
    let currentPath = '';
    
    if (keyName) {
      // 有key的行
      pathStack[level] = { key: keyName, isArray: isArrayStart, arrayIndex: -1, startLineIndex: index, level };
      
      // 构建路径字符串：从根到当前层级
      const pathParts: string[] = [];
      for (let i = 0; i <= level; i++) {
        if (pathStack[i]) {
          pathParts.push(pathStack[i].key);
          // 如果这一层是数组，并且有有效的索引，添加索引
          if (pathStack[i].isArray && pathStack[i].arrayIndex >= 0) {
            pathParts.push(String(pathStack[i].arrayIndex));
          }
        }
      }
      currentPath = pathParts.join('.');
      
      // 如果是可折叠的开始，记录索引（空数组/对象不需要push，因为开始和结束在同一行）
      if ((isArrayStart || isObjectStart) && !isEmptyBracket) {
        foldableStack.push(index);
      }
    } else if (isObjectInArray) {
      // 数组中的对象元素 - 增加父级数组的索引
      if (level > 0 && pathStack[level - 1]?.isArray) {
        pathStack[level - 1].arrayIndex++;
        
        // 构建路径：到父级（包括父级数组索引）
        const pathParts: string[] = [];
        for (let i = 0; i < level; i++) {
          if (pathStack[i]) {
            pathParts.push(pathStack[i].key);
            // 如果这一层是数组，并且有有效的索引，添加索引
            if (pathStack[i].isArray && pathStack[i].arrayIndex >= 0) {
              pathParts.push(String(pathStack[i].arrayIndex));
            }
          }
        }
        currentPath = pathParts.join('.');
      }
      
      // 数组中的对象也可能需要记录折叠范围
      foldableStack.push(index);
    } else if (isArrayInArray) {
      // 数组中的数组元素 - 增加父级数组的索引
      if (level > 0 && pathStack[level - 1]?.isArray) {
        pathStack[level - 1].arrayIndex++;
        
        // 在当前层级创建一个数组项（用于追踪嵌套数组）
        pathStack[level] = { 
          key: '', 
          isArray: true, 
          arrayIndex: -1, // 当前数组的索引会在值行时递增
          startLineIndex: index, 
          level 
        };
        
        // 构建路径：包含所有层级的路径和索引
        // 注意：pathStack[level-1] 的索引刚刚被递增了，需要包含在路径中
        const pathParts: string[] = [];
        for (let i = 0; i <= level - 1; i++) {
          if (pathStack[i]) {
            const item = pathStack[i];
            if (item.key) {
              // 有key的项，添加key和索引
              pathParts.push(item.key);
              if (item.isArray && item.arrayIndex >= 0) {
                pathParts.push(String(item.arrayIndex));
              }
            } else if (item.isArray) {
              // 数组项（key为空），只添加索引
              if (item.arrayIndex >= 0) {
                pathParts.push(String(item.arrayIndex));
              }
            }
          }
        }
        currentPath = pathParts.join('.');
      }
      
      // 数组中的数组也需要记录折叠范围
      foldableStack.push(index);
    } else {
      // 括号或值行 - 使用当前栈构建路径
      const pathParts: string[] = [];

      // 如果当前行是数组内的值行（不是括号，不是key），需要处理数组索引
      // 注意：由于已经在 else 分支中，isArrayInArray 和 isObjectInArray 一定为 false
      if (level > 0 && !isCloseBracket && !keyName) {
        // 向上查找最近的数组父级（包括有key的数组和数组中的数组）
        let arrayParentLevel = -1;
        let arrayParentHasKey = false;
        for (let i = level - 1; i >= 0; i--) {
          if (pathStack[i]?.isArray) {
            // 找到数组父级
            arrayParentLevel = i;
            arrayParentHasKey = pathStack[i].key !== '';
            break;
          }
        }
        
        if (arrayParentLevel >= 0) {
          if (arrayParentHasKey) {
            // 值行属于有key的数组（如 projects），需要递增该数组的索引
            if (pathStack[arrayParentLevel].arrayIndex < 0) {
              pathStack[arrayParentLevel].arrayIndex = 0;
            } else {
              pathStack[arrayParentLevel].arrayIndex++;
            }
          }
          
          // 构建路径：包含到 arrayParentLevel 的所有路径和索引
          // 清空之前的内容，重新构建
          pathParts.length = 0;
          for (let i = 0; i <= arrayParentLevel; i++) {
            if (pathStack[i]) {
              const item = pathStack[i];
              if (item.key === '' && item.isArray) {
                // 数组项（数组中的数组），添加索引
                if (item.arrayIndex >= 0) {
                  pathParts.push(String(item.arrayIndex));
                }
              } else if (item.key) {
                // 有key的项
                pathParts.push(item.key);
                // 如果这一层是数组，并且有有效的索引，添加索引
                if (item.isArray && item.arrayIndex >= 0) {
                  pathParts.push(String(item.arrayIndex));
                }
              }
            }
          }
          currentPath = pathParts.join('.');
        } else {
          // 没有找到数组父级，使用标准路径构建
          for (let i = 0; i <= level; i++) {
            if (pathStack[i]) {
              const item = pathStack[i];
              if (item.key === '' && item.isArray) {
                // 数组项（数组中的数组），添加索引
                if (item.arrayIndex >= 0) {
                  pathParts.push(String(item.arrayIndex));
                }
              } else if (item.key) {
                // 有key的项
                pathParts.push(item.key);
                // 如果这一层是数组，并且有有效的索引，添加索引
                if (item.isArray && item.arrayIndex >= 0) {
                  pathParts.push(String(item.arrayIndex));
                }
              }
            }
          }
          currentPath = pathParts.join('.');
        }
      } else {
        // 构建完整路径（非值行）
        for (let i = 0; i <= level; i++) {
          if (pathStack[i]) {
            const item = pathStack[i];
            if (item.key === '' && item.isArray) {
              // 数组项（数组中的数组），添加索引
              if (item.arrayIndex >= 0) {
                pathParts.push(String(item.arrayIndex));
              }
            } else if (item.key) {
              // 有key的项
              pathParts.push(item.key);
              // 如果这一层是数组，并且有有效的索引，添加索引
              if (item.isArray && item.arrayIndex >= 0) {
                pathParts.push(String(item.arrayIndex));
              }
            }
          }
        }
        currentPath = pathParts.join('.');
      }
    }
    
    // 判断是否可折叠
    // 1. 有key的对象/数组可折叠
    // 2. 数组中的对象元素也可折叠
    // 3. 数组中的数组元素也可折叠
    // 注意：空数组/对象也可以折叠（虽然只有一行）
    const foldable = ((isArrayStart || isObjectStart) && keyName !== '') || isObjectInArray || isArrayInArray;
    
    // 如果是结束括号且有继承的path，使用继承的path
    // 但是空数组/对象的结束括号不继承path（因为开始和结束在同一行）
    const finalPath = (isCloseBracket && closeBracketPath && !isEmptyBracket) ? closeBracketPath : currentPath;
    
    // 对于空数组/对象，endLineIndex 就是当前行
    let endLineIndexValue: number | undefined = undefined;
    if (isEmptyBracket) {
      endLineIndexValue = index;
    } else if (isCloseBracket && closeBracketPath) {
      // 结束括号的 endLineIndex 不需要设置（它是结束行）
      endLineIndexValue = undefined;
    }
    
    lines.push({
      content: line,
      path: finalPath,
      foldable,
      level,
      type: keyName ? 'key' : (/^[[\]{},?]$/.test(trimmedLine) ? 'bracket' : 'value'),
      keyName,
      lineIndex: index,
      endLineIndex: endLineIndexValue,
    });
  });
  
  return lines;
};

// 合并左右两侧的行，实现对齐效果
const mergedLines = computed((): MergedLine[] => {
  const oldLines = parseJsonToLines(props.oldJson);
  const newLines = parseJsonToLines(props.newJson);
  
  const merged: MergedLine[] = [];
  let oldLineNumber = 1;
  let newLineNumber = 1;
  
  // 构建路径到行的映射
  const oldPathToLines = new Map<string, JsonLine[]>();
  const newPathToLines = new Map<string, JsonLine[]>();
  
  oldLines.forEach(line => {
    if (line.path) {
      if (!oldPathToLines.has(line.path)) {
        oldPathToLines.set(line.path, []);
      }
      oldPathToLines.get(line.path)!.push(line);
    }
  });
  
  newLines.forEach(line => {
    if (line.path) {
      if (!newPathToLines.has(line.path)) {
        newPathToLines.set(line.path, []);
      }
      newPathToLines.get(line.path)!.push(line);
    }
  });
  
  let oldIndex = 0;
  let newIndex = 0;
  
  while (oldIndex < oldLines.length || newIndex < newLines.length) {
    const oldLine = oldIndex < oldLines.length ? oldLines[oldIndex] : null;
    const newLine = newIndex < newLines.length ? newLines[newIndex] : null;
    
    if (!oldLine && !newLine) {
      break;
    }
    
    // 检查折叠状态
    const oldFolded = oldLine && shouldSkipFoldedLine(oldLine, oldLines, oldIndex);
    const newFolded = newLine && shouldSkipFoldedLine(newLine, newLines, newIndex);
    
    // 处理折叠情况：更新索引并跳过后续处理
    if (oldFolded && newFolded) {
      oldIndex++;
      newIndex++;
    } else if (oldFolded) {
      oldIndex++;
    } else if (newFolded) {
      newIndex++;
    } else {
      // 获取差异信息
      const oldDiff = oldLine?.path ? props.diffMap.get(oldLine.path) : null;
      const newDiff = newLine?.path ? props.diffMap.get(newLine.path) : null;
      
      // 检查是否是某个新增/删除字段的子路径
      const oldIsChildOfDeleted = oldLine?.path ? isChildOfDiffPath(oldLine.path, 'deleted') : false;
      const newIsChildOfAdded = newLine?.path ? isChildOfDiffPath(newLine.path, 'added') : false;
      
      if (oldLine && newLine) {
        // 判断是否是同一个逻辑行
        const isSamePath = oldLine.path && newLine.path && oldLine.path === newLine.path;
        
        if (isSamePath) {
          // 相同路径
          const diff = props.diffMap.get(oldLine.path);
          
          // 检查路径是否被折叠（分别检查左右两边）
          const oldFoldablePath = oldLine.path || `_line_${oldLine.lineIndex}`;
          const newFoldablePath = newLine.path || `_line_${newLine.lineIndex}`;
          const oldIsFolded = oldLine.foldable && foldedPaths.value.has(oldFoldablePath);
          const newIsFolded = newLine.foldable && foldedPaths.value.has(newFoldablePath);
          
          // 如果任意一边被折叠，需要处理折叠逻辑
          if (oldIsFolded || newIsFolded) {
            // 只显示开始行（会被折叠显示为 [...] 或 {...}）
            merged.push({
              oldLine,
              newLine,
              oldLineNumber: oldLineNumber++,
              newLineNumber: newLineNumber++,
            });
            // 跳过折叠范围
            if (oldIsFolded && oldLine.endLineIndex !== undefined) {
              oldIndex = oldLine.endLineIndex + 1;
            } else {
              oldIndex++;
            }
            if (newIsFolded && newLine.endLineIndex !== undefined) {
              newIndex = newLine.endLineIndex + 1;
            } else {
              newIndex++;
            }
          } else {
            // 检查是否是修改类型
            if (diff?.type === 'modified') {
              // 计算两边的行数
              const oldEndIndex = oldLine.endLineIndex ?? oldIndex;
              const newEndIndex = newLine.endLineIndex ?? newIndex;
              const oldLineCount = oldEndIndex - oldIndex + 1;
              const newLineCount = newEndIndex - newIndex + 1;
              
              // 如果行数不同，需要对齐
              if (oldLineCount !== newLineCount) {
                const maxLineCount = Math.max(oldLineCount, newLineCount);
                
                // 对齐显示（需要逐行检查折叠状态）
                let i = 0;
                while (i < maxLineCount) {
                  const oldLineAtI = i < oldLineCount ? oldLines[oldIndex + i] : null;
                  const newLineAtI = i < newLineCount ? newLines[newIndex + i] : null;
                  
                  // 检查是否是被折叠的开始行
                  const oldFoldablePathAtI = oldLineAtI ? getFoldablePath(oldLineAtI) : '';
                  const newFoldablePathAtI = newLineAtI ? getFoldablePath(newLineAtI) : '';
                  const oldIsFoldedStart = oldLineAtI?.foldable && foldedPaths.value.has(oldFoldablePathAtI);
                  const newIsFoldedStart = newLineAtI?.foldable && foldedPaths.value.has(newFoldablePathAtI);
                  
                  // 检查是否在折叠范围内（但不是开始行）
                  const oldFoldedAtI = oldLineAtI && shouldSkipFoldedLine(oldLineAtI, oldLines, oldIndex + i);
                  const newFoldedAtI = newLineAtI && shouldSkipFoldedLine(newLineAtI, newLines, newIndex + i);
                  
                  if (oldIsFoldedStart && newIsFoldedStart) {
                    // 两边都是被折叠的开始行，显示开始行并跳过范围
                    merged.push({
                      oldLine: oldLineAtI,
                      newLine: newLineAtI,
                      oldLineNumber: oldLineNumber++,
                      newLineNumber: newLineNumber++,
                    });
                    // 跳过折叠范围
                    if (oldLineAtI && oldLineAtI.endLineIndex !== undefined) {
                      const skipCount = oldLineAtI.endLineIndex - (oldIndex + i) + 1;
                      i += skipCount;
                    } else {
                      i++;
                    }
                  } else if (oldIsFoldedStart) {
                    // 左边是被折叠的开始行，显示开始行并跳过范围
                    merged.push({
                      oldLine: oldLineAtI,
                      newLine: newLineAtI,
                      oldLineNumber: oldLineNumber++,
                      newLineNumber: newLineAtI ? newLineNumber++ : 0,
                    });
                    if (oldLineAtI && oldLineAtI.endLineIndex !== undefined) {
                      const skipCount = oldLineAtI.endLineIndex - (oldIndex + i) + 1;
                      i += skipCount;
                    } else {
                      i++;
                    }
                  } else if (newIsFoldedStart) {
                    // 右边是被折叠的开始行，显示开始行并跳过范围
                    merged.push({
                      oldLine: oldLineAtI,
                      newLine: newLineAtI,
                      oldLineNumber: oldLineAtI ? oldLineNumber++ : 0,
                      newLineNumber: newLineNumber++,
                    });
                    if (newLineAtI && newLineAtI.endLineIndex !== undefined) {
                      const skipCount = newLineAtI.endLineIndex - (newIndex + i) + 1;
                      i += skipCount;
                    } else {
                      i++;
                    }
                  } else if (oldFoldedAtI || newFoldedAtI) {
                    // 在折叠范围内（但不是开始行），跳过
                    i++;
                  } else {
                    // 都没折叠，正常处理
                    if (i < oldLineCount && i < newLineCount) {
                      // 两边都有内容，配对显示
                      merged.push({
                        oldLine: oldLineAtI,
                        newLine: newLineAtI,
                        oldLineNumber: oldLineNumber++,
                        newLineNumber: newLineNumber++,
                      });
                    } else if (i < oldLineCount) {
                      // 只有左边有内容，右边补空行
                      merged.push({
                        oldLine: oldLineAtI,
                        newLine: null,
                        oldLineNumber: oldLineNumber++,
                        newLineNumber: 0,
                      });
                    } else {
                      // 只有右边有内容，左边补空行
                      merged.push({
                        oldLine: null,
                        newLine: newLineAtI,
                        oldLineNumber: 0,
                        newLineNumber: newLineNumber++,
                      });
                    }
                    i++;
                  }
                }
                oldIndex += oldLineCount;
                newIndex += newLineCount;
              } else {
                // 行数相同，直接配对
                for (let i = 0; i < oldLineCount; i++) {
                  merged.push({
                    oldLine: oldLines[oldIndex + i],
                    newLine: newLines[newIndex + i],
                    oldLineNumber: oldLineNumber++,
                    newLineNumber: newLineNumber++,
                  });
                }
                oldIndex += oldLineCount;
                newIndex += newLineCount;
              }
            } else {
              // 单行修改或非折叠内容，直接配对
              merged.push({
                oldLine,
                newLine,
                oldLineNumber: oldLineNumber++,
                newLineNumber: newLineNumber++,
              });
              oldIndex++;
              newIndex++;
            }
          }
        } else if (oldDiff?.type === 'deleted' || oldIsChildOfDeleted) {
          // 左侧删除（或删除字段的子行） - 右侧显示空行
          // 检查是否被折叠
          const foldablePath = oldLine.path || `_line_${oldLine.lineIndex}`;
          const isFolded = oldLine.foldable && foldedPaths.value.has(foldablePath);
          
          if (isFolded && oldLine.endLineIndex !== undefined) {
            // 被折叠，只显示开始行，跳过整个范围
            merged.push({
              oldLine,
              newLine: null,
              oldLineNumber: oldLineNumber++,
              newLineNumber: 0,
            });
            oldIndex = oldLine.endLineIndex + 1;
          } else {
            merged.push({
              oldLine,
              newLine: null,
              oldLineNumber: oldLineNumber++,
              newLineNumber: 0,
            });
            oldIndex++;
          }
        } else if (newDiff?.type === 'added' || newIsChildOfAdded) {
          // 右侧新增（或新增字段的子行） - 左侧显示空行
          // 检查是否被折叠
          const foldablePath = newLine.path || `_line_${newLine.lineIndex}`;
          const isFolded = newLine.foldable && foldedPaths.value.has(foldablePath);
          
          if (isFolded && newLine.endLineIndex !== undefined) {
            // 被折叠，只显示开始行，跳过整个范围
            merged.push({
              oldLine: null,
              newLine,
              oldLineNumber: 0,
              newLineNumber: newLineNumber++,
            });
            newIndex = newLine.endLineIndex + 1;
          } else {
            merged.push({
              oldLine: null,
              newLine,
              oldLineNumber: 0,
              newLineNumber: newLineNumber++,
            });
            newIndex++;
          }
        } else {
          // 默认配对
          merged.push({
            oldLine,
            newLine,
            oldLineNumber: oldLineNumber++,
            newLineNumber: newLineNumber++,
          });
          oldIndex++;
          newIndex++;
        }
      } else if (oldLine) {
        // 只有左侧 - 删除
        // 检查是否被折叠
        const foldablePath = oldLine.path || `_line_${oldLine.lineIndex}`;
        const isFolded = oldLine.foldable && foldedPaths.value.has(foldablePath);
        
        if (isFolded && oldLine.endLineIndex !== undefined) {
          // 被折叠，只显示开始行，跳过整个范围
          merged.push({
            oldLine,
            newLine: null,
            oldLineNumber: oldLineNumber++,
            newLineNumber: 0,
          });
          oldIndex = oldLine.endLineIndex + 1;
        } else {
          merged.push({
            oldLine,
            newLine: null,
            oldLineNumber: oldLineNumber++,
            newLineNumber: 0,
          });
          oldIndex++;
        }
      } else if (newLine) {
        // 只有右侧 - 新增
        // 检查是否被折叠
        const foldablePath = newLine.path || `_line_${newLine.lineIndex}`;
        const isFolded = newLine.foldable && foldedPaths.value.has(foldablePath);
        
        if (isFolded && newLine.endLineIndex !== undefined) {
          // 被折叠，只显示开始行，跳过整个范围
          merged.push({
            oldLine: null,
            newLine,
            oldLineNumber: 0,
            newLineNumber: newLineNumber++,
          });
          newIndex = newLine.endLineIndex + 1;
        } else {
          merged.push({
            oldLine: null,
            newLine,
            oldLineNumber: 0,
            newLineNumber: newLineNumber++,
          });
          newIndex++;
        }
      }
    }
  }
  
  return merged;
});

// 获取可折叠行的唯一路径（提前声明，供其他函数使用）
const getFoldablePath = (line: JsonLine): string => {
  // 如果有path就用path，否则用行号作为唯一标识（用于数组中的对象元素）
  return line.path || `_line_${line.lineIndex}`;
};

// 检查是否应该跳过折叠的行
const shouldSkipFoldedLine = (line: JsonLine, allLines: JsonLine[], _currentIndex: number): boolean => {
  // 使用 line.lineIndex 作为当前行的真实索引（在 allLines 中的索引）
  const lineIndex = line.lineIndex;
  
  // 检查该行是否在某个已折叠的范围内
  for (let i = 0; i < lineIndex; i++) {
    const prevLine = allLines[i];
    if (prevLine && prevLine.foldable && prevLine.endLineIndex !== undefined) {
      const prevFoldablePath = getFoldablePath(prevLine);
      if (foldedPaths.value.has(prevFoldablePath)) {
        // 如果当前行在折叠范围内（包括结束行）
        // 折叠时，开始行显示为 [...] 或 {...}，中间所有行和结束行都应该隐藏
        if (lineIndex > i && lineIndex <= prevLine.endLineIndex) {
          return true;
        }
      }
    }
  }
  
  return false;
};

// 检查路径是否是某个差异路径的子路径
const isChildOfDiffPath = (linePath: string, diffType: 'added' | 'deleted' | 'modified'): boolean => {
  // 遍历 diffMap 中的所有路径
  for (const [diffPath, diff] of props.diffMap.entries()) {
    if (diff.type === diffType) {
      // 检查 linePath 是否以 diffPath 开头（且后面跟着点号或者完全相同）
      if (linePath === diffPath || linePath.startsWith(diffPath + '.')) {
        return true;
      }
    }
  }
  return false;
};

// 获取行的CSS类
const getLineClass = (line: JsonLine | null, side: 'old' | 'new'): string[] => {
  const classes: string[] = [];
  
  if (!line) {
    // 空行（用于对齐）
    return classes;
  }
  
  if (!line.path) {
    return classes;
  }
  
  // 检查是否被忽略
  const isIgnored = props.ignoredKeys.has(line.path);
  const isIgnoredAndShown = isIgnored && props.showIgnored;
  
  // 首先检查精确匹配
  const diff = props.diffMap.get(line.path);
  
  if (diff && !isIgnored) {
    if (diff.type === 'added') {
      // 新增：右侧绿色，左侧不标记
      if (side === 'new') {
        classes.push('diff-added');
      }
    } else if (diff.type === 'deleted') {
      // 删除：左侧红色，右侧不标记
      if (side === 'old') {
        classes.push('diff-deleted');
      }
    } else if (diff.type === 'modified') {
      // 修改：左右都标记黄色
      classes.push('diff-modified');
    }
  } else if (!isIgnored) {
    // 没有精确匹配，检查是否是某个差异路径的子路径
    if (side === 'new' && isChildOfDiffPath(line.path, 'added')) {
      // 新增字段的子路径也标记为新增
      classes.push('diff-added');
    } else if (side === 'old' && isChildOfDiffPath(line.path, 'deleted')) {
      // 删除字段的子路径也标记为删除
      classes.push('diff-deleted');
    } else if (isChildOfDiffPath(line.path, 'modified')) {
      // 修改字段的子路径也标记为修改
      classes.push('diff-modified');
    }
  } else if (isIgnoredAndShown) {
    // 忽略字段且显示时，添加灰色背景
    classes.push('diff-ignored');
  }
  
  return classes;
};

// 高亮JSON语法
const highlightJson = (line: string): string => {
  let highlighted = line;
  
  // 高亮字符串
  highlighted = highlighted.replace(/"([^"]*)":/g, '<span class="json-key">"$1"</span>:');
  highlighted = highlighted.replace(/:\s*"([^"]*)"/g, ': <span class="json-string">"$1"</span>');
  
  // 高亮数字
  highlighted = highlighted.replace(/:\s*(-?\d+\.?\d*)/g, ': <span class="json-number">$1</span>');
  
  // 高亮布尔值和null
  highlighted = highlighted.replace(/:\s*(true|false|null)/g, ': <span class="json-boolean">$1</span>');
  
  return highlighted;
};

// 获取高亮后的内容（包含折叠处理）
const getHighlightedContent = (line: JsonLine): string => {
  // 先进行语法高亮
  let content = highlightJson(line.content);
  
  // 获取折叠路径
  const foldablePath = getFoldablePath(line);
  
  // 如果该行被折叠，添加折叠标记
  if (line.foldable && foldedPaths.value.has(foldablePath)) {
    const trimmed = line.content.trim();
    if (trimmed.endsWith('[')) {
      // 有key的数组折叠：在高亮后的内容中添加省略号
      content = content.replace(/\[$/, '[<span class="fold-ellipsis">...</span>],');
    } else if (trimmed === '[' || trimmed === '[,') {
      // 数组中的数组折叠：替换整行为 [...]
      const indent = line.content.match(/^\s*/)?.[0] || '';
      content = indent + '[<span class="fold-ellipsis">...</span>],' + (trimmed === '[,' ? ',' : '');
    } else if (trimmed.endsWith('{') || trimmed === '{' || trimmed === '{,') {
      // 对象折叠：在高亮后的内容中添加省略号
      content = content.replace(/\{,?$/, '{<span class="fold-ellipsis">...</span>},');
    }
  }
  
  return content;
};

// 切换折叠状态
const toggleFold = (path: string) => {
  if (foldedPaths.value.has(path)) {
    foldedPaths.value.delete(path);
  } else {
    foldedPaths.value.add(path);
  }
  // 触发响应式更新
  foldedPaths.value = new Set(foldedPaths.value);
};


// 复制整份 JSON 对象
const handleCopy = async (json: Record<string, unknown>, label: string) => {
  try {
    const text = JSON.stringify(json, null, 2);
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const textArea = document.createElement('textarea');
      textArea.value = text;
      textArea.style.position = 'fixed';
      textArea.style.opacity = '0';
      textArea.style.left = '-9999px';
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();
      document.execCommand('copy');
      document.body.removeChild(textArea);
    }
    message.success(`已复制${label}到剪贴板`);
  } catch (error) {
    console.error('复制JSON失败:', error);
    message.error('复制失败');
  }
};

// 复制jsonPath
const handleCopyPath = async (path: string) => {
  try {
    // 将数组索引从点号格式转换为方括号格式
    // 例如：data.0.abCode -> data[0].abCode
    const formattedPath = path.replace(/\.(\d+)(?=\.|$)/g, '[$1]');
    
    const textArea = document.createElement('textarea');
    textArea.value = `$.${formattedPath}`;
    textArea.style.position = 'fixed';
    textArea.style.opacity = '0';
    textArea.style.left = '-9999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    document.execCommand('copy');
    document.body.removeChild(textArea);
    message.success('已复制到剪贴板');
  } catch (error) {
    console.error('复制失败:', error);
    message.error('复制失败');
  }
};

// 定位到指定key的行
const scrollToKey = async (key: string) => {
  // 等待DOM更新
  await nextTick();
  
  // 在mergedLines中找到包含该key的行（优先找开始行）
  let targetLineIndex = -1;
  
  // 先查找精确匹配的行（通常是key行）
  targetLineIndex = mergedLines.value.findIndex(line => {
    const oldPath = line.oldLine?.path || '';
    const newPath = line.newLine?.path || '';
    return (oldPath === key && line.oldLine?.keyName) || 
           (newPath === key && line.newLine?.keyName);
  });
  
  // 如果没找到精确匹配，查找包含该key的任何行（包括子路径）
  if (targetLineIndex === -1) {
    targetLineIndex = mergedLines.value.findIndex(line => {
      const oldPath = line.oldLine?.path || '';
      const newPath = line.newLine?.path || '';
      return oldPath === key || newPath === key ||
             oldPath.startsWith(key + '.') || newPath.startsWith(key + '.');
    });
  }
  
  if (targetLineIndex === -1) {
    return;
  }
  
  // 获取目标行的diff类型
  const targetLine = mergedLines.value[targetLineIndex];
  let highlightType: DiffType | 'normal' = 'normal';
  
  // 检查左侧行的diff类型
  if (targetLine.oldLine?.path) {
    const oldDiff = props.diffMap.get(targetLine.oldLine.path);
    const isIgnored = props.ignoredKeys.has(targetLine.oldLine.path) && !props.showIgnored;
    if (oldDiff && !isIgnored) {
      if (oldDiff.type === 'added') {
        highlightType = 'added';
      } else if (oldDiff.type === 'deleted') {
        highlightType = 'deleted';
      } else if (oldDiff.type === 'modified') {
        highlightType = 'modified';
      }
    }
  }
  
  // 检查右侧行的diff类型（如果左侧没有diff，使用右侧的）
  if (highlightType === 'normal' && targetLine.newLine?.path) {
    const newDiff = props.diffMap.get(targetLine.newLine.path);
    const isIgnored = props.ignoredKeys.has(targetLine.newLine.path) && !props.showIgnored;
    if (newDiff && !isIgnored) {
      if (newDiff.type === 'added') {
        highlightType = 'added';
      } else if (newDiff.type === 'deleted') {
        highlightType = 'deleted';
      } else if (newDiff.type === 'modified') {
        highlightType = 'modified';
      }
    }
  }
  
  // 再次等待DOM更新
  await nextTick();
  
  // 添加小延迟确保DOM完全渲染
  setTimeout(() => {
    // 滚动到目标行
    if (leftContentRef.value && rightContentRef.value) {
      const leftPanel = leftContentRef.value;
      const rightPanel = rightContentRef.value;
      
      // 获取目标行元素
      const leftLines = leftPanel.querySelectorAll('.json-line');
      const rightLines = rightPanel.querySelectorAll('.json-line');
      
      if (leftLines[targetLineIndex] && rightLines[targetLineIndex]) {
        const leftTarget = leftLines[targetLineIndex] as HTMLElement;
        const rightTarget = rightLines[targetLineIndex] as HTMLElement;
        
        // 设置程序化滚动标志，临时禁用同步滚动
        isProgrammaticScrolling.value = true;
        
        // 使用 getBoundingClientRect 获取准确位置
        const leftRect = leftTarget.getBoundingClientRect();
        const rightRect = rightTarget.getBoundingClientRect();
        const leftPanelRect = leftPanel.getBoundingClientRect();
        const rightPanelRect = rightPanel.getBoundingClientRect();
        
        // 计算目标行相对于滚动容器的位置
        const leftTargetTop = leftRect.top - leftPanelRect.top + leftPanel.scrollTop;
        const rightTargetTop = rightRect.top - rightPanelRect.top + rightPanel.scrollTop;
        
        // 计算滚动到中间的位置
        const leftScrollTop = leftTargetTop - leftPanel.clientHeight / 2 + leftTarget.offsetHeight / 2;
        const rightScrollTop = rightTargetTop - rightPanel.clientHeight / 2 + rightTarget.offsetHeight / 2;
        
        // 平滑滚动
        leftPanel.scrollTo({
          top: Math.max(0, leftScrollTop),
          behavior: 'smooth'
        });
        
        rightPanel.scrollTo({
          top: Math.max(0, rightScrollTop),
          behavior: 'smooth'
        });
        
        // 滚动完成后恢复同步滚动并执行闪烁动画
        setTimeout(() => {
          isProgrammaticScrolling.value = false;
          
          // 滚动完成后设置高亮行索引和类型（用于闪烁动画）
          highlightedLineIndex.value = targetLineIndex;
          highlightedLineIndexType.value = highlightType;

          // 0.5秒后清除高亮（只闪烁一次）
          const delay = 500;
          setTimeout(() => {
            highlightedLineIndex.value = null;
            highlightedLineIndexType.value = null;
          }, delay);
        }, 1000);
      }
    }
  }, 100);
};

// 暴露方法给父组件
defineExpose({
  scrollToKey
});

// 同步滚动
onMounted(() => {
  if (leftContentRef.value && rightContentRef.value) {
    const leftPanel = leftContentRef.value;
    const rightPanel = rightContentRef.value;
    
    let isLeftScrolling = false;
    let isRightScrolling = false;
    
    leftPanel.addEventListener('scroll', () => {
      // 如果正在程序化滚动，不执行同步
      if (isProgrammaticScrolling.value) {
        return;
      }
      if (!isRightScrolling) {
        isLeftScrolling = true;
        rightPanel.scrollTop = leftPanel.scrollTop;
        setTimeout(() => {
          isLeftScrolling = false;
        }, 100);
      }
    });
    
    rightPanel.addEventListener('scroll', () => {
      // 如果正在程序化滚动，不执行同步
      if (isProgrammaticScrolling.value) {
        return;
      }
      if (!isLeftScrolling) {
        isRightScrolling = true;
        leftPanel.scrollTop = rightPanel.scrollTop;
        setTimeout(() => {
          isRightScrolling = false;
        }, 100);
      }
    });
  }
});
</script>

<style scoped lang="less">
.json-diff-viewer-wrapper {
  display: flex;
  gap: 0px;
  margin-top: 20px;

  .json-diff-panel {
    flex: 1;
    overflow: scroll;
    background-color: #fff;
    display: flex;
    flex-direction: column;

    .json-diff-panel__header {
      position: sticky;
      top: 0;
      z-index: 100;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      min-height: 40px;
      padding: 10px 12px;
      background-color: transparent;
      border-bottom: 1px solid #e5e6eb;
      flex-shrink: 0;
    }

    .json-diff-panel__title {
      font-size: 14px;
      font-weight: 600;
      color: #1d2129;
      flex-shrink: 0;
    }

    .json-diff-panel__copy-btn {
      flex-shrink: 0;
      width: 28px;
      height: 28px;
      min-width: 28px;
      padding: 4px;
      opacity: 0.85;
      transition: opacity 0.2s, background-color 0.2s;

      &:hover {
        opacity: 1;
        background-color: rgba(64, 158, 255, 0.08);
      }
    }
    /* genAI_feature_story_197624_end */

    .json-diff-panel-content {
      max-height: 600px;
      height: 600px;
      min-width: max-content;
      overflow: auto;
      padding: 0px;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
      font-size: 13px;
      line-height: 1.6;
      display: flex;
      flex-direction: column;

      .json-line {
        display: flex;
        align-items: center;
        padding: 2px 0;
        transition: background-color 0.2s;
        position: relative;

        min-width: max-content;

        &:hover {
          background-color: #f5f7fa;
        }

        .line-number {
          display: inline-block;
          width: 32px;
          margin-right: 6px;
          padding-right: 6px;
          text-align: right;
          color: #999;
          border-right: 1px solid #e4e7ed;
          user-select: none;
          flex-shrink: 0;
        }

        .fold-icon {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 16px;
          margin-right: 2px;
          cursor: pointer;
          user-select: none;
          color: #606266;
          font-size: 11px;
          flex-shrink: 0;

          &:hover {
            color: #409eff;
          }
        }

        .fold-placeholder {
          display: inline-block;
          width: 16px;
          margin-right: 2px;
          flex-shrink: 0;
        }

        .copy-icon {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 16px;
          margin-right: 2px;
          cursor: pointer;
          user-select: none;
          color: #909399;
          font-size: 11px;
          transition: color 0.2s;
          flex-shrink: 0;

          &:hover {
            color: #409eff;
          }
        }

        .copy-placeholder {
          display: inline-block;
          width: 16px;
          margin-right: 2px;
          flex-shrink: 0;
        }

        .line-content {
          flex: 1;
          white-space: pre;
          min-height: 20.8px;

          :deep(.json-key) {
            color: #e96900;
            font-weight: 500;
          }

          :deep(.json-string) {
            color: #3ab54a;
          }

          :deep(.json-number) {
            color: #25aae2;
          }

          :deep(.json-boolean) {
            color: #f98280;
          }

          :deep(.fold-ellipsis) {
            color: #999;
            border-radius: 4px;
            padding: 2px 6px;
            margin: 0 6px;
            background-color: #f0f0f0;
            font-style: italic;
          }
        }

        // 差异标记样式
        &.diff-added {
          background-color: rgba(16, 185, 129, 0.1);

          .line-content :deep(.json-key),
          .line-content :deep(.json-string),
          .line-content :deep(.json-number),
          .line-content :deep(.json-boolean) {
            font-weight: 600;
          }
        }

        &.diff-deleted {
          background-color: rgba(239, 68, 68, 0.1);

          .line-content :deep(.json-key),
          .line-content :deep(.json-string),
          .line-content :deep(.json-number),
          .line-content :deep(.json-boolean) {
            font-weight: 600;
          }
        }

        &.diff-modified {
          background-color: rgba(251, 191, 36, 0.1);

          .line-content :deep(.json-key),
          .line-content :deep(.json-string),
          .line-content :deep(.json-number),
          .line-content :deep(.json-boolean) {
            font-weight: 600;
          }
        }

        &.diff-ignored {
          background-color: #bcb8b1;

          .line-content :deep(.json-key),
          .line-content :deep(.json-string),
          .line-content :deep(.json-number),
          .line-content :deep(.json-boolean) {
            color: #666;
            font-weight: 400;
          }
        }

        // 高亮闪烁动画（根据行类型设置不同颜色）
        &.highlight-normal {
          animation: highlightBlinkNormal 0.5s ease-in-out;
          background-color: rgba(144, 147, 153, 0.2);
        }

        &.highlight-added {
          animation: highlightBlinkAdded 0.5s ease-in-out;
          background-color: rgba(16, 185, 129, 0.2);
        }

        &.highlight-deleted {
          animation: highlightBlinkDeleted 0.5s ease-in-out;
          background-color: rgba(239, 68, 68, 0.2);
        }

        &.highlight-modified {
          animation: highlightBlinkModified 0.5s ease-in-out;
          background-color: rgba(251, 191, 36, 0.2);
        }
      }
    }
  }
}

// 闪烁动画（只闪烁一次）
@keyframes highlightBlinkNormal {
  0%, 100% {
    background-color: rgba(144, 147, 153, 0.1);
    border-left-color: #909399;
  }
  50% {
    background-color: rgba(144, 147, 153, 0.3);
    border-left-color: #b1b3b8;
  }
}

@keyframes highlightBlinkAdded {
  0%, 100% {
    background-color: rgba(16, 185, 129, 0.1);
    border-left-color: #10b981;
  }
  50% {
    background-color: rgba(16, 185, 129, 0.3);
    border-left-color: #34d399;
  }
}

@keyframes highlightBlinkDeleted {
  0%, 100% {
    background-color: rgba(239, 68, 68, 0.1);
    border-left-color: #ef4444;
  }
  50% {
    background-color: rgba(239, 68, 68, 0.3);
    border-left-color: #f87171;
  }
}

@keyframes highlightBlinkModified {
  0%, 100% {
    background-color: rgba(251, 191, 36, 0.1);
    border-left-color: #fbbf24;
  }
  50% {
    background-color: rgba(251, 191, 36, 0.3);
    border-left-color: #fcd34d;
  }
}
</style>