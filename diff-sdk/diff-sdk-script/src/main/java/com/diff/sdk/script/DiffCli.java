package com.diff.sdk.script;

import com.diff.sdk.model.DiffConfig;
import com.diff.sdk.model.DiffResult;
import com.diff.sdk.JsonDiffers;
import com.diff.sdk.JsonDiffer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令行入口：对比两个 JSON 文件。
 *
 * <pre>
 *   java -jar diff-sdk-script-0.1.0-SNAPSHOT.jar old.json new.json
 *   java -jar diff-sdk-script-0.1.0-SNAPSHOT.jar old.json new.json --ignore 'meta.**' --max-depth 5
 * </pre>
 *
 * <p>退出码：{@code 0} 无差异、{@code 1} 有差异、{@code 2} 用法或 IO 错误。
 */
public final class DiffCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiffCli() {
    }

    public static void main(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage());
            usage();
            System.exit(2);
            return;
        }

        if (options.help) {
            usage();
            System.exit(0);
            return;
        }

        try {
            String oldJson = read(options.oldFile);
            String newJson = read(options.newFile);

            DiffConfig config = DiffConfig.builder()
                    .ignorePaths(options.ignorePaths)
                    .maxDepth(options.maxDepth)
                    .lenientNumbers(!options.strictNumbers)
                    .build();
            JsonDiffer differ = JsonDiffers.create(config);
            DiffResult result = differ.diffJson(oldJson, newJson, config);

            System.out.println(options.pretty
                    ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result.toMap())
                    : result.toJson());

            System.exit(result.isEmpty() ? 0 : 1);
        } catch (IllegalArgumentException e) {
            System.err.println("错误: " + e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("读取文件失败: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void usage() {
        System.err.println("用法: java -jar diff-sdk-script.jar <old.json> <new.json> [options]");
        System.err.println();
        System.err.println("options:");
        System.err.println("  --ignore <pattern>   忽略路径模式，可重复（如 'meta.**'、'*.timestamp'）");
        System.err.println("  --max-depth <n>      最大递归深度，超过后整棵子树按整体值比较");
        System.err.println("  --strict-numbers     关闭数字宽松比较（1 与 1.0 视为不同）");
        System.err.println("  --pretty             缩进输出");
        System.err.println("  -h, --help           显示帮助");
        System.err.println();
        System.err.println("退出码: 0 无差异 / 1 有差异 / 2 用法或 IO 错误");
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    /** 命令行参数 */
    private static final class Options {
        private String oldFile;
        private String newFile;
        private final List<String> ignorePaths = new ArrayList<>();
        private int maxDepth = DiffConfig.UNLIMITED_DEPTH;
        private boolean strictNumbers;
        private boolean pretty;
        private boolean help;

        private static Options parse(String[] args) {
            Options options = new Options();
            List<String> positional = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h":
                    case "--help":
                        options.help = true;
                        break;
                    case "--ignore":
                        options.ignorePaths.add(requireValue(args, ++i, "--ignore"));
                        break;
                    case "--max-depth":
                        options.maxDepth = Integer.parseInt(requireValue(args, ++i, "--max-depth"));
                        break;
                    case "--strict-numbers":
                        options.strictNumbers = true;
                        break;
                    case "--pretty":
                        options.pretty = true;
                        break;
                    default:
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("未知参数: " + arg);
                        }
                        positional.add(arg);
                        break;
                }
            }
            if (options.help) {
                return options;
            }
            if (positional.size() != 2) {
                throw new IllegalArgumentException("需要两个 JSON 文件路径，实际给了 " + positional.size() + " 个");
            }
            options.oldFile = positional.get(0);
            options.newFile = positional.get(1);
            return options;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " 缺少取值");
            }
            return args[index];
        }
    }
}
