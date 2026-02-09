package cal.injector;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;

import static arthas.VmTool.JNI_LIBRARY_NAME;


public class Executor {

    private static final String agentLoaderName = "agent-loader-1.0-SNAPSHOT.jar";

    private final String appNameQuery;

    public Executor(String appNameQuery) {
        this.appNameQuery = appNameQuery;
    }

    public static void main(String[] args0) throws IOException {
        String[] args = new String[4];
//        pid
        String appNameQuery2 = args0[0];
        args[0] = findJVMProcess(appNameQuery2);
//       testClass
        args[1] = args0[1];
        parepareAndSetResource(Executor.class, args, appNameQuery2);
        Injector.main(args);
    }

    @SuppressWarnings("ReturnOfNull")
    public static String findJVMProcess(String appNameQuery) {
        try {
            System.out.println("[Executor] Looking for JVM process containing: " + appNameQuery);

            // Get current process PID to exclude it from matching
            String currentPid = getCurrentPid();
            System.out.println("[Executor] Current process PID (excluded): " + currentPid);

            java.util.List<VirtualMachineDescriptor> matchingProcesses = new java.util.ArrayList<>();

            for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
                String displayName = vmd.displayName();
                String pid = vmd.id();

                // Skip current process
                if (pid.equals(currentPid)) {
                    continue;
                }

                if (displayName != null && displayName.toLowerCase().contains(appNameQuery.toLowerCase())) {
                    matchingProcesses.add(vmd);
                    System.out.println("[Executor] Found matching process: PID=" + pid + ", Name='" + displayName + "'");
                }
            }

            if (matchingProcesses.isEmpty()) {
                System.err.println("[Executor] No JVM process found containing: " + appNameQuery);
                System.err.println("[Executor] Hint: The display name might be different from 'jps -lvm' output");
                System.err.println("[Executor] Try using the main class name instead of the JAR name");
                return null;
            }

            if (matchingProcesses.size() > 1) {
                System.err.println("[Executor] WARNING: Multiple matching processes found (" + matchingProcesses.size() + ")!");
                System.err.println("[Executor] Using the first one: PID=" + matchingProcesses.get(0).id());
                System.err.println("[Executor] Consider using a more specific process name or kill old processes");
            }

            return matchingProcesses.get(0).id();
        } catch (Throwable e) {
            System.out.println("this will not reach, can not be caught");
            if (e instanceof java.util.ServiceConfigurationError) {
                System.err.println(e.getMessage());
                return null;
            } else {
                throw e;
            }
        }
    }

    /**
     * Gets the current process PID using Java 8 compatible method.
     */
    private static String getCurrentPid() {
        String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        // Format: "pid@hostname"
        return jvmName.split("@")[0];
    }

    @SuppressWarnings("unused")
    static long getPidBefore9() {
        String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        long pid = Long.parseLong(jvmName.split("@")[0]);
        System.out.println("Current PID: " + pid);
        return pid;
    }

    @SuppressWarnings("unused")
    public static String getPid(String appNameQuery) throws IOException {
        try {
            // Java 9+ official way
            return findProcessJava9Plus(appNameQuery);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            return findProcessJava8(appNameQuery);
        }
    }

    @SuppressWarnings("unused")
    static long getPidAfter9() {
//        long pid = ProcessHandle.current().pid();
//        System.out.println("Current PID: " + pid);
//        return pid;
        throw new RuntimeException("not java 9+");
    }

    @SuppressWarnings("unused")
    static String findProcessJava9Plus(String targetName) {
        throw new RuntimeException("not java 9+");
/*
        ProcessHandle.allProcesses()
                     .filter(ph -> ph.info().command().orElse("").contains(targetName) ||
                             ph.info().arguments().map(argsArr -> String.join(" ", argsArr)).orElse("")
                               .contains(targetName))
                     .forEach(ph -> {
                         System.out.println("Found Process: " + ph.pid() + " | Command: " + ph.info().command()
                                                                                              .orElse("N/A"));
                     });
        return pid;
*/
    }

    @SuppressWarnings({"CallToRuntimeExec", "ReturnOfNull"})
    static String findProcessJava8(String appNameQuery) throws IOException {
        Process p = Runtime.getRuntime().exec("jps -l");
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(appNameQuery)) {
                String pid = line.split(" ")[0];
                System.out.println("Found PID: " + pid);
                return pid;
            }
        }

        return null;
    }


    public static void copyDirectory(String sourceDir, String targetDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);

        // Create target directory if it doesn't exist
        Files.createDirectories(targetPath);

        // Copy all files and directories
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.forEach(source -> {
                try {
                    Path destination = targetPath.resolve(sourcePath.relativize(source));

                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + source, e);
                }
            });
        }
    }

    public static String getTestClassPath(Class klass) {
        return getClassPath(klass);
    }

    private static String getClassPath(Class klass) {
        String classFilePath = getClassLocationFile(klass);

        File classesFolder = new File(classFilePath);
        if (classesFolder.isFile()) {
            classesFolder = classesFolder.getParentFile();
        }
        return classesFolder.getAbsolutePath();
    }

    private static String getClassLocationFile(Class klass) {
        return klass.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getFile();
    }


    private static void parepareAndSetResource(Class<?> aClass, String[] args, String appNameQuery) throws IOException {
        ResourcePrepared resourcePrepared = prepareResource(aClass, appNameQuery);
        args[2] = resourcePrepared.resourceDir;
        args[3] = resourcePrepared.agentPath;
    }

    @SuppressWarnings("JvmTaintAnalysis")
    private static ResourcePrepared prepareResource(Class<?> aClass, String appNameQuery) throws IOException {
        File resourceDir = new File(System.getProperty("java.io.tmpdir") + File.separator + appNameQuery + ".test");
        if (!resourceDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            resourceDir.mkdirs();
        }
        File testClassDir = new File(getTestClassPath(aClass));
        assert testClassDir.exists();
// test class
        copyDirectory(testClassDir.getAbsolutePath(), resourceDir.getAbsolutePath());
// agent lib
        File agentLib = new File(getClassLocationFile(Executor.class));
        File agentLibPath = new File(resourceDir, agentLib.getName());
        if (!agentLibPath.exists()) {
            Files.copy(Paths.get(agentLib.getAbsolutePath()), Paths.get(agentLibPath.getAbsolutePath()));
        }
// dll for jni
        String libraryName = System.mapLibraryName(JNI_LIBRARY_NAME);
        File jniLibPath = new File(resourceDir, libraryName);
        if (!jniLibPath.exists()) {
            Files.copy(Objects.requireNonNull(Executor.class.getResourceAsStream("/" + libraryName)), Paths.get(jniLibPath.getAbsolutePath()));
        }
// agent loader
        File agentLoaderFile = new File(resourceDir, agentLoaderName);
        if (!agentLoaderFile.exists()) {
            try (InputStream agentLoader = Executor.class.getResourceAsStream("/" + agentLoaderName)) {
                assert agentLoader != null;
                Files.copy(agentLoader, Paths.get(agentLoaderFile.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new ResourcePrepared(agentLoaderFile.getAbsolutePath(), resourceDir.getAbsolutePath());
    }

    public void execute(Method executable, Class<?> aClass) throws IOException {
        String[] args = new String[4];
//        pid
        args[0] = findJVMProcess(appNameQuery);
//       testClass
        args[1] = aClass.getCanonicalName() + "#" + executable.getName();
//        depDir
        parepareAndSetResource(aClass, args, appNameQuery);
        Injector.main(args);
    }

    static class ResourcePrepared {
        public String agentPath;
        public String resourceDir;

        public ResourcePrepared(String agentPath, String resourceDir) {
            this.agentPath = agentPath;
            this.resourceDir = resourceDir;
        }

    }

}
