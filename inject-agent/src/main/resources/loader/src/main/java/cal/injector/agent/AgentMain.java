package cal.injector.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AgentMain {
    // Static field to hold VmTool instance across multiple agent runs
    private static Object vmToolInstance = null;

    private static ClassLoader findAppClassLoader(Instrumentation inst) {
        String[] springIndicatorClasses = {
                "org.springframework.boot.SpringApplication",
                "org.springframework.web.servlet.DispatcherServlet",
                "org.springframework.context.ApplicationContext",
                "org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext"
        };

        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            for (String indicator : springIndicatorClasses) {
                if (clazz.getName().equals(indicator)) {
                    ClassLoader classLoader = clazz.getClassLoader();
                    System.out.println("[Agent] Found app classloader via " + indicator + ": " + classLoader);
                    return classLoader;
                }
            }
        }

        Thread currentThread = Thread.currentThread();
        ClassLoader contextClassLoader = currentThread.getContextClassLoader();
        System.out.println("[Agent] Falling back to thread context classloader: " + contextClassLoader);
        return contextClassLoader;
    }

    private static synchronized Object getOrCreateVmTool(ClassLoader testClassLoader, String libPath) {
        if (vmToolInstance != null) {
            System.out.println("[Agent] Reusing existing VmTool instance");
            return vmToolInstance;
        }

        try {
            Class<?> vmToolClass = Class.forName("arthas.VmTool", true, testClassLoader);
            Method getInstanceMethod = vmToolClass.getMethod("getInstance", String.class);
            vmToolInstance = getInstanceMethod.invoke(null, libPath);
            System.out.println("[Agent] Created new VmTool instance");
            return vmToolInstance;
        } catch (Exception e) {
            System.err.println("[Agent] Failed to create VmTool: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] Agent attached/re-attached.");
        try {
            if (agentArgs == null || agentArgs.isEmpty()) {
                System.err.println("[Agent] No arguments provided.");
                return;
            }

            String[] parts = agentArgs.split("\\|");
            if (parts.length < 2) {
                System.err.println("[Agent] Invalid arguments. Expected: TestClassName|PathToDependencies");
                return;
            }

            String testClassName = parts[0];
            String methodName = null;
            if (testClassName.contains("#")) {
                String[] split = testClassName.split("#");
                testClassName = split[0];
                methodName = split[1];
            }
            String depDirStr = parts[1];
            File depDir = new File(depDirStr);

            if (!depDir.exists() || !depDir.isDirectory()) {
                System.err.println("[Agent] Dependency directory does not exist: " + depDirStr);
                return;
            }

            System.out.println("[Agent] Target Test Class: " + testClassName);
            System.out.println("[Agent] Loading dependencies from: " + depDir.getAbsolutePath());

            List<URL> urls = new ArrayList<>();
            urls.add(depDir.toURI().toURL());

            File[] files = depDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (files != null) {
                for (File f : files) {
                    URL url = f.toURI().toURL();
                    urls.add(url);
                    System.out.println("[Agent] Add dependency: " + url);
                }
            }

            ClassLoader appClassLoader = findAppClassLoader(inst);
            System.out.println("[Agent] App ClassLoader: " + appClassLoader);

            // Create NEW ClassLoader for hot reload
            URLClassLoader testLoader = new URLClassLoader(urls.toArray(new URL[0]), appClassLoader);
            System.out.println("[Agent] Created new test ClassLoader for hot reload");

            // Initialize VmTool using test ClassLoader
            String libraryName = System.mapLibraryName("ArthasJniLibrary");
            String libPath = depDir.getAbsolutePath() + File.separator + libraryName;
            Object vmTool = getOrCreateVmTool(testLoader, libPath);
            if (vmTool == null) {
                System.err.println("[Agent] Failed to initialize VmTool");
                return;
            }

            Class<?> testClass;
            try {
                testClass = testLoader.loadClass(testClassName);
            } catch (ClassNotFoundException e) {
                System.err.println("[Agent] Could not load class: " + testClassName);
                e.printStackTrace();
                return;
            }

            System.out.println("[Agent] Class loaded (" + testClass.getName() + "). Executing tests...");

            // Inject VmTool
            try {
                Class<?> baseAgentTestClass = testLoader.loadClass("cal.runner.junit5.BaseAgentTest");
                Method setVmToolMethod = baseAgentTestClass.getMethod("setVmTool", Object.class);
                setVmToolMethod.invoke(null, vmTool);
                System.out.println("[Agent] Injected VmTool into BaseAgentTest");
            } catch (Exception e) {
                System.err.println("[Agent] Failed to inject VmTool: " + e.getMessage());
            }

            Object instance = testClass.getDeclaredConstructor().newInstance();

            if (methodName != null) {
                for (Method m : testClass.getDeclaredMethods()) {
                    m.setAccessible(true);
                    if (methodName.equals(m.getName())) {
                        runMethod(m, instance);
                        break;
                    }
                }
            } else {
                boolean ran = false;
                for (Method m : testClass.getDeclaredMethods()) {
                    m.setAccessible(true);
                    boolean isTest = false;
                    for (java.lang.annotation.Annotation a : m.getAnnotations()) {
                        String name = a.annotationType().getName();
                        if (name.equals("org.junit.Test") || name.equals("org.junit.jupiter.api.Test")) {
                            isTest = true;
                            break;
                        }
                    }
                    if (isTest) {
                        runMethod(m, instance);
                        ran = true;
                    }
                }
                if (!ran) {
                    System.out.println("[Agent] No @Test methods found.");
                }
            }

        } catch (Throwable t) {
            System.err.println("[Agent] Error during injection execution.");
            t.printStackTrace();
        }
    }

    private static void runMethod(Method m, Object instance) {
        System.out.println("[Agent] Running test method: " + m.getName());
        try {
            m.invoke(instance);
        } catch (Throwable t) {
            System.err.println("[Agent] " + m.getName() + " FAILED");
            t.printStackTrace();
        }
    }
}
