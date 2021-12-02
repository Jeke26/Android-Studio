package com.tyron.builder.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class TestUtil {

    public static boolean isWindows() {
        return System.getProperty("os.name", "")
                .startsWith("Windows");
    }

    public static File getResourcesDirectory() throws IOException {
        File currentDirFile = Paths.get(".").toFile();
        String helper = currentDirFile.getAbsolutePath();
        String currentDir = helper.substring(0,
                helper.length() - 1);
        return new File(new File(currentDir), "src/test/resources");
    }
}
