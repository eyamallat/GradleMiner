package org.example.parser;

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactory;

public final class KotlinParser {
    private static final String IN_MEMORY_FILE_NAME = "build.gradle.kts";

    private KotlinParser() {
    }

    public static ParsedKotlinFile parseModule(String source) {
        Disposable disposable = Disposer.newDisposable();

        try {
            CompilerConfiguration configuration = new CompilerConfiguration();
            configuration.put(CommonConfigurationKeys.MODULE_NAME, "refminer-kotlin-dsl");

            KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(
                    disposable,
                    configuration,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES
            );

            KtPsiFactory psiFactory = new KtPsiFactory(environment.getProject(), false);
            KtFile file = psiFactory.createFile(IN_MEMORY_FILE_NAME, source);

            return new ParsedKotlinFile(disposable, file);
        } catch (RuntimeException e) {
            Disposer.dispose(disposable);
            throw e;
        }
    }

    public static final class ParsedKotlinFile implements AutoCloseable {
        private final Disposable disposable;
        private final KtFile file;

        private ParsedKotlinFile(Disposable disposable, KtFile file) {
            this.disposable = disposable;
            this.file = file;
        }

        public KtFile file() {
            return file;
        }

        @Override
        public void close() {
            Disposer.dispose(disposable);
        }
    }
}