package org.example.refactoring;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;

import java.util.*;

public final class DslTreeUtils {
    private DslTreeUtils() {
    }

    public static boolean isDescendantOf(DSLNode node, DSLNode ancestor) {
        for (DSLNode cur = node; cur != null; cur = cur.parent) {
            if (cur == ancestor) return true;
        }
        return false;
    }

    public static boolean sameFile(DSLNode a, DSLNode b) {
        if (a == null || b == null) return false;
        String pa = normalizePath(a.relativePath);
        String pb = normalizePath(b.relativePath);
        return !pa.isBlank() && pa.equals(pb);
    }

    public static String nodeLocationName(DSLNode node) {
        if (node == null) return "";

        String path = normalizePath(node.relativePath);
        String name = safe(node.name);
        int line = node.line;

        String base;
        if (!path.isBlank() && !name.isBlank()) {
            base = path + ":" + name;
        } else if (!path.isBlank()) {
            base = path;
        } else {
            base = name;
        }

        if (line > 0) {
            return base + " " + "(" + line + ")";
        }

        return base;
    }

    public static String normalizePath(String path) {
        return safe(path).replace('\\', '/');
    }

    public static String directoryOf(String path) {
        String p = normalizePath(path);
        int i = p.lastIndexOf('/');
        return i < 0 ? "" : p.substring(0, i);
    }

    public static List<String> pathSegments(String path) {
        String d = directoryOf(path);
        if (d.isBlank()) return List.of();
        return Arrays.stream(d.split("/")).filter(x -> !x.isBlank()).toList();
    }

    public static int directoryDepth(String path) {
        return pathSegments(path).size();
    }

    public static boolean isParentBuildFileOf(String parentPath, String childPath) {
        String p = normalizePath(parentPath);
        String c = normalizePath(childPath);
        if (p.isBlank() || c.isBlank() || p.equals(c)) return false;

        List<String> ps = pathSegments(p);
        List<String> cs = pathSegments(c);
        if (ps.size() >= cs.size()) return false;

        for (int i = 0; i < ps.size(); i++) {
            if (!ps.get(i).equals(cs.get(i))) return false;
        }
        return true;
    }

    public static boolean isPullUp(String sourcePath, String targetPath) {
        return directoryDepth(sourcePath) > directoryDepth(targetPath)
                && isParentBuildFileOf(targetPath, sourcePath);
    }

    public static boolean isPushDown(String sourcePath, String targetPath) {
        return directoryDepth(sourcePath) < directoryDepth(targetPath)
                && isParentBuildFileOf(sourcePath, targetPath);
    }

    public static boolean isMove(String sourcePath, String targetPath) {
        String s = normalizePath(sourcePath);
        String t = normalizePath(targetPath);
        if (s.isBlank() || t.isBlank() || s.equals(t)) return false;
        return !isPullUp(s, t) && !isPushDown(s, t);
    }

    public static boolean isFile(DSLNode n) {
        if (n == null) return false;
        return n.type == DSLNode.Type.PROPERTIES_FILE
                || n.type == DSLNode.Type.BUILD_GRADLE
                || n.type == DSLNode.Type.BUILD_LOGIC_GRADLE
                || n.type == DSLNode.Type.SETTINGS_GRADLE
                || n.type == DSLNode.Type.INIT_GRADLE;
    }

    public static boolean isGradleFile(DSLNode n) {
        if (n == null) return false;
        return n.type == DSLNode.Type.BUILD_GRADLE
                || n.type == DSLNode.Type.BUILD_LOGIC_GRADLE
                || n.type == DSLNode.Type.SETTINGS_GRADLE
                || n.type == DSLNode.Type.INIT_GRADLE;
    }

    public static DSLNode fileRootOf(DSLNode n) {
        for (DSLNode cur = n; cur != null; cur = cur.parent) {
            if (isFile(cur)) return cur;
        }
        return null;
    }

    public static boolean sameLogicalConsumerFile(DSLNode before, DSLNode after, RefactoringContext context) {
        DSLNode beforeFile = fileRootOf(before);
        DSLNode afterFile = fileRootOf(after);

        if (beforeFile == null || afterFile == null) return false;

        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.left() == null || p.right() == null) continue;

            if (p.left() == beforeFile && p.right() == afterFile) {
                return true;
            }
        }

        return sameMovedBuildFileName(beforeFile.relativePath, afterFile.relativePath);
    }

    public static boolean isGradlePath(String path) {
        String p = normalizePath(path);
        return p.endsWith(".gradle")
                || p.endsWith(".gradle.kts")
                || p.endsWith("settings.gradle")
                || p.endsWith("settings.gradle.kts")
                || p.endsWith("init.gradle")
                || p.endsWith("init.gradle.kts");
    }

    public static boolean isGradleNode(DSLNode node) {
        return node != null && isGradlePath(node.relativePath);
    }

    public static List<DSLNode> flatten(DSLNode root) {
        List<DSLNode> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    public static void walk(DSLNode node, List<DSLNode> out) {
        if (node == null) return;
        out.add(node);
        for (DSLNode c : safeList(node.children)) {
            walk(c, out);
        }
    }

    public static int depth(DSLNode node) {
        int d = 0;
        for (DSLNode cur = node == null ? null : node.parent; cur != null; cur = cur.parent) d++;
        return d;
    }

    public static void addIfAbsent(List<DSLNode> nodes, DSLNode node) {
        if (node != null && !nodes.contains(node)) nodes.add(node);
    }

    public static String nodeKey(DSLNode n) {
        if (n == null) return "null";
        return n.type + "|" + norm(n.name) + "|" + normalizePath(n.relativePath)
                + "|" + (n.value == null ? "" : n.value.contentKey())
                + "|" + (n.semantic == null ? "" : String.valueOf(n.semantic));
    }

    private static boolean sameMovedBuildFileName(String beforePath, String afterPath) {
        String before = normalizePath(beforePath);
        String after = normalizePath(afterPath);

        if (before.isBlank() || after.isBlank()) return false;
        if (before.equals(after)) return true;

        return fileName(before).equals(fileName(after))
                && isGradlePath(before)
                && isGradlePath(after);
    }

    private static String fileName(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String norm(String s) {
        return safe(s).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
