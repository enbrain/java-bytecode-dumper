package io.github.enbrain.javabytecodedumper;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.internal.core.util.MementoTokenizer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 2) {
            byte[] data = switch (args[0]) {
                case "file" -> Files.readAllBytes(Path.of(args[1]));
                case "jdt" -> {
                    FileInFilePath classFilePath = parseMemento(args[1]);
                    if (classFilePath != null) {
                        try (FileSystem fs = FileSystems.newFileSystem(Path.of(classFilePath.outerPath))) {
                            yield Files.readAllBytes(fs.getPath(classFilePath.innerPath));
                        }
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
                default -> throw new IllegalArgumentException();
            };
            dump(data);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static FileInFilePath parseMemento(String memento) {
        MementoTokenizer tokenizer = new MementoTokenizer(memento);

        if (!tokenizer.hasMoreTokens() || tokenizer.nextToken() != MementoTokenizer.JAVAPROJECT) {
            return null;
        }

        tokenizer.nextToken();

        if (!tokenizer.hasMoreTokens() || tokenizer.nextToken() != MementoTokenizer.PACKAGEFRAGMENTROOT) {
            return null;
        }

        String rootPath = "";

        String token = null;

        while (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            if (token == MementoTokenizer.PACKAGEFRAGMENT || token == MementoTokenizer.COUNT) {
                break;
            } else if (token == MementoTokenizer.MODULE) {
                if (tokenizer.hasMoreTokens()) {
                    token = tokenizer.nextToken();
                }
                continue;
            } else if (token == MementoTokenizer.CLASSPATH_ATTRIBUTE) {
                tokenizer.getStringDelimitedBy(MementoTokenizer.CLASSPATH_ATTRIBUTE);
                tokenizer.getStringDelimitedBy(MementoTokenizer.CLASSPATH_ATTRIBUTE);
                token = null;
                continue;
            }
            rootPath += token;
        }

        if (token == null && tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
        }

        if (token != MementoTokenizer.PACKAGEFRAGMENT) {
            return null;
        }

        String packageName;

        if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            if (token == MementoTokenizer.CLASSFILE
                    || token == MementoTokenizer.MODULAR_CLASSFILE
                    || token == MementoTokenizer.COMPILATIONUNIT
                    || token == MementoTokenizer.COUNT) {
                packageName = "";
            } else {
                packageName = token;
                token = null;
            }
        } else {
            packageName = "";
            token = null;
        }

        if (token == null && tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
        }

        if (token != MementoTokenizer.CLASSFILE) {
            return null;
        }

        if (!tokenizer.hasMoreTokens()) {
            return null;
        }

        String classFileName = tokenizer.nextToken();

        String innerPath = packageName.replace('.', '/') + "/" + classFileName;

        return new FileInFilePath(rootPath, innerPath);

    }

    private static record FileInFilePath(String outerPath, String innerPath) {

    }

    private static void dump(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);
        PrintWriter writer = new PrintWriter(System.out);
        TraceClassVisitor tracer = new TraceClassVisitor(null, new Textifier(), writer);
        reader.accept(tracer, 0);
    }
}
