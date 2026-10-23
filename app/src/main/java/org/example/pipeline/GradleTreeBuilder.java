package org.example.pipeline;

import org.codehaus.groovy.ast.ModuleNode;
import org.example.github.CommitInfo;
import org.example.github.GitHubCommitClient;
import org.example.model.DSLNode;
import org.example.parser.GroovyParser;
import org.example.parser.KotlinParser;
import org.example.visitor.GroovyVisitor;
import org.example.visitor.KotlinVisitor;
import org.jetbrains.kotlin.psi.KtFile;

import java.util.List;
import java.util.Optional;

import static org.example.parser.PropertiesParser.attachParentAndPathRecursively;
import static org.example.parser.PropertiesParser.parseProperties;

public class GradleTreeBuilder {
    private final GitHubCommitClient github;

    public GradleTreeBuilder(GitHubCommitClient github) {
        this.github = github;
    }

    public DSLNode buildTree(CommitInfo info, String sha, List<String> files) {
        DSLNode root = new DSLNode("ROOT_PROJECT");
        root.type = DSLNode.Type.ROOT_PROJECT;

        for (String path : files) {
            Optional<String> contentOpt = github.fetchFileAtCommit(info, sha, path);
            if (contentOpt.isEmpty()) {
                continue;
            }

            try {
                DSLNode fileNode = null;

                if (path.endsWith(".properties")) {
                    fileNode = parseProperties(contentOpt.get(), path);
                } else if (path.endsWith(".gradle.kts")) {
                    fileNode = parseKotlinFile(contentOpt.get(), path);
                } else if (path.endsWith(".gradle")) {
                    fileNode = parseGroovyFile(contentOpt.get(), path);
                }

                if (fileNode != null) {
                    fileNode.parent = root;
                    root.children.add(fileNode);
                }

            } catch (Exception ignored) {
            }
        }

        return root;
    }

    public static DSLNode emptyFileNodeLike(DSLNode fileNode) {
        DSLNode empty = new DSLNode(fileNode.name);
        empty.type = fileNode.type;
        empty.relativePath = fileNode.relativePath;
        return empty;
    }

    private static DSLNode parseGroovyFile(String content, String filename) {
        ModuleNode module = GroovyParser.parseModule(content);
        GroovyVisitor visitor = new GroovyVisitor();
        visitor.visitModule(module);

        DSLNode fileNode = new DSLNode(filename);

        if (filename.startsWith("settings")) {
            fileNode.type = DSLNode.Type.SETTINGS_GRADLE;
        } else if (filename.startsWith("init")) {
            fileNode.type = DSLNode.Type.INIT_GRADLE;
        } else if (filename.endsWith("build.gradle")) {
            fileNode.type = DSLNode.Type.BUILD_GRADLE;
        } else if (filename.endsWith(".gradle")) {
            fileNode.type = DSLNode.Type.BUILD_LOGIC_GRADLE;
        }

        fileNode.relativePath = filename;

        for (DSLNode child : visitor.getRoots()) {
            attachParentAndPathRecursively(child, fileNode, filename);
            fileNode.children.add(child);
        }

        return fileNode;
    }

    private static DSLNode parseKotlinFile(String content, String filename) {
        try (KotlinParser.ParsedKotlinFile parsed = KotlinParser.parseModule(content)) {
            KtFile module = parsed.file();

            DSLNode fileNode = new DSLNode(filename);

            if (filename.startsWith("settings")) {
                fileNode.type = DSLNode.Type.SETTINGS_GRADLE;
            } else if (filename.startsWith("init")) {
                fileNode.type = DSLNode.Type.INIT_GRADLE;
            } else if (filename.endsWith("build.gradle.kts")) {
                fileNode.type = DSLNode.Type.BUILD_GRADLE;
            } else if (filename.endsWith(".gradle.kts")) {
                fileNode.type = DSLNode.Type.BUILD_LOGIC_GRADLE;
            }

            fileNode.relativePath = filename;

            KotlinVisitor visitor = new KotlinVisitor(fileNode, filename);
            module.accept(visitor);

            return fileNode;
        }
    }
}
