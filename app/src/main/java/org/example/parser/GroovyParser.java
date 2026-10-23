package org.example.parser;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.SourceUnit;


public class GroovyParser {

    public static ModuleNode parseModule(String source)
            throws CompilationFailedException {

        CompilerConfiguration config = new CompilerConfiguration();
        GroovyClassLoader loader = new GroovyClassLoader();
        ErrorCollector errorCollector = new ErrorCollector(config);

        SourceUnit unit = new SourceUnit(
                "script.groovy",
                source,
                config,
                loader,
                errorCollector
        );

        unit.parse();
        unit.completePhase();
        unit.convert();

        return unit.getAST();
    }

}