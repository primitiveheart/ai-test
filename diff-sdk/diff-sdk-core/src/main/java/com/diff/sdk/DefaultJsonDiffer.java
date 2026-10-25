package com.diff.sdk;

import com.diff.sdk.model.DiffConfig;
import com.diff.sdk.model.DiffItem;
import com.diff.sdk.model.DiffResult;
import com.diff.sdk.model.DiffType;
import com.diff.sdk.model.DiffValues;
import com.diff.sdk.model.PathPatterns;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 默认的递归 JSON 对比实现，无状态、线程安全。
 *
 * <h3>对比语义</h3>
 * <ul>
 *   <li><b>ADDED</b>：路径仅在新值中存在（旧值为 null / 缺失）。</li>
 *   <li><b>DELETED</b>：路径仅在旧值中存在（新值为 null / 缺失）。</li>
 *   <li><b>MODIFIED</b>：两侧都存在但值不同，或一侧是容器另一侧是标量。</li>
 * </ul>
 *
 * <p>容器（对象 / 数组）会继续下钻，差异记录到叶子节点；只有容器与标量类型冲突，
 * 或受 {@link DiffConfig#getMaxDepth()} 限制无法下钻时，才把整棵子树作为一个值比较。
 *
 * <p>{@link DiffConfig#getIgnorePaths()} 命中的路径会先从两棵树中清洗掉再对比，
 * 因此被忽略的子树不会产生任何差异项。
 */
final class DefaultJsonDiffer implements JsonDiffer {

    private final ObjectMapper mapper;
    private final DiffConfig config;

    DefaultJsonDiffer(ObjectMapper mapper, DiffConfig config) {
        this.mapper = mapper;
        this.config = config;
    }

    @Override
    public DiffResult diffJson(String oldJson, String newJson, DiffConfig config) {
        DiffConfig cfg = config == null ? this.config : config;
        JsonNode oldNode = parse(oldJson, "oldValue");
        JsonNode newNode = parse(newJson, "newValue");
        return diff(oldNode, newNode, cfg);
    }

    @Override
    public DiffResult diff(JsonNode oldNode, JsonNode newNode, DiffConfig config) {
        DiffConfig cfg = config == null ? this.config : config;

        JsonNode oldRoot = oldNode == null ? NullNode.getInstance() : oldNode;
        JsonNode newRoot = newNode == null ? NullNode.getInstance() : newNode;

        // 只有声明了忽略路径才做清洗；未清洗时结果里不携带清洗后的 JSON
        boolean sanitizing = !cfg.getIgnorePaths().isEmpty();
        JsonNode oldClean = sanitizing ? sanitize(oldRoot, cfg) : oldRoot;
        JsonNode newClean = sanitizing ? sanitize(newRoot, cfg) : newRoot;

        List<DiffItem> items = new ArrayList<>();
        walk("", oldClean, newClean, cfg, items);

        return new DiffResult(items, sanitizing ? oldClean : null, sanitizing ? newClean : null);
    }

    // ---------------------------------------------------------------- 解析

    private JsonNode parse(String json, String label) {
        if (json == null || json.trim().isEmpty()) {
            return NullNode.getInstance();
        }
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException(label + " 不是合法 JSON: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- 递归对比

    private void walk(String path, JsonNode oldNode, JsonNode newNode, DiffConfig cfg, List<DiffItem> items) {
        if (cfg.isIgnored(path)) {
            return;
        }

        boolean oldPresent = isPresent(oldNode);
        boolean newPresent = isPresent(newNode);

        if (!oldPresent && !newPresent) {
            return;
        }
        if (!oldPresent) {
            items.add(new DiffItem(path, DiffType.ADDED, null, valueOf(newNode)));
            return;
        }
        if (!newPresent) {
            items.add(new DiffItem(path, DiffType.DELETED, valueOf(oldNode), null));
            return;
        }

        boolean oldContainer = oldNode.isContainerNode();
        boolean newContainer = newNode.isContainerNode();

        // 容器 vs 标量：无法下钻，整棵子树当成一个值比较
        if (oldContainer != newContainer) {
            addIfDifferent(items, path, oldNode, newNode);
            return;
        }

        if (!oldContainer) {
            if (!equalValues(oldNode, newNode, cfg)) {
                items.add(new DiffItem(path, DiffType.MODIFIED, valueOf(oldNode), valueOf(newNode)));
            }
            return;
        }

        // 对象 vs 数组：同样无法下钻
        if (oldNode.isObject() != newNode.isObject()) {
            addIfDifferent(items, path, oldNode, newNode);
            return;
        }

        // 达到深度上限，整棵子树按整体值比较
        if (PathPatterns.split(path).size() >= cfg.getMaxDepth()) {
            addIfDifferent(items, path, oldNode, newNode);
            return;
        }

        if (oldNode.isObject()) {
            walkObject(path, oldNode, newNode, cfg, items);
        } else {
            walkArray(path, oldNode, newNode, cfg, items);
        }
    }

    private void walkObject(String path, JsonNode oldNode, JsonNode newNode, DiffConfig cfg, List<DiffItem> items) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Iterator<String> oldNames = oldNode.fieldNames();
        while (oldNames.hasNext()) {
            names.add(oldNames.next());
        }
        Iterator<String> newNames = newNode.fieldNames();
        while (newNames.hasNext()) {
            names.add(newNames.next());
        }
        for (String name : names) {
            walk(childPath(path, name), oldNode.get(name), newNode.get(name), cfg, items);
        }
    }

    private void walkArray(String path, JsonNode oldNode, JsonNode newNode, DiffConfig cfg, List<DiffItem> items) {
        int size = Math.max(oldNode.size(), newNode.size());
        for (int i = 0; i < size; i++) {
            walk(childPath(path, String.valueOf(i)), oldNode.get(i), newNode.get(i), cfg, items);
        }
    }

    private void addIfDifferent(List<DiffItem> items, String path, JsonNode oldNode, JsonNode newNode) {
        if (!oldNode.equals(newNode)) {
            items.add(new DiffItem(path, DiffType.MODIFIED, valueOf(oldNode), valueOf(newNode)));
        }
    }

    /** JSON 标量相等判定；开启宽松比较时，1 与 1.0 视为相等（跨类型仍判为不同） */
    private boolean equalValues(JsonNode a, JsonNode b, DiffConfig cfg) {
        if (cfg.isLenientNumberComparison() && a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        return a.equals(b);
    }

    /** 节点是否存在：复用 {@link DiffValues#isAbsent(Object)}，保证全局口径一致 */
    private static boolean isPresent(JsonNode node) {
        return !DiffValues.isAbsent(node);
    }

    /** 差异项里的值统一以 JsonNode 保存，序列化时保持原始 JSON 结构 */
    private static JsonNode valueOf(JsonNode node) {
        return node == null ? NullNode.getInstance() : node;
    }

    private static String childPath(String parent, String segment) {
        return parent.isEmpty() ? segment : parent + "." + segment;
    }

    // ---------------------------------------------------------------- 忽略路径清洗

    /** 返回剔除了忽略路径的树副本；根节点不会被剔除 */
    private JsonNode sanitize(JsonNode root, DiffConfig cfg) {
        if (root == null || !root.isContainerNode()) {
            return root;
        }
        return sanitizeNode("", root, cfg);
    }

    private JsonNode sanitizeNode(String path, JsonNode node, DiffConfig cfg) {
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = childPath(path, field.getKey());
                if (cfg.isIgnored(childPath)) {
                    continue;
                }
                out.set(field.getKey(), sanitizeNode(childPath, field.getValue(), cfg));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            for (int i = 0; i < node.size(); i++) {
                String childPath = childPath(path, String.valueOf(i));
                if (cfg.isIgnored(childPath)) {
                    continue;
                }
                out.add(sanitizeNode(childPath, node.get(i), cfg));
            }
            return out;
        }
        return MissingNode.getInstance().equals(node) ? NullNode.getInstance() : node;
    }
}
