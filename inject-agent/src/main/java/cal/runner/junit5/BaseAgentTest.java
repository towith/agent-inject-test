package cal.runner.junit5;

import cal.injector.Executor;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

@SuppressWarnings("unused")
@ExtendWith(Runner5.class)
public class BaseAgentTest {
    static {
        printClassLoaderHierarchy();
    }

    protected static Object vmTool;

    // Called by Agent to inject VmTool instance (as Object to avoid classloader issues)
    @SuppressWarnings("unused")
    public static void setVmTool(Object tool) {
        vmTool = tool;
    }

    @SuppressWarnings("unused")
    protected static ClassLoader findAppClassLoader(Instrumentation inst) {
        Thread currentThread = Thread.currentThread();
        return currentThread.getContextClassLoader();
    }

    protected static <T> T[] getVmToolInstances(Class<T> klass, int limit) {
        try {
            if (vmTool == null) {
                throw new RuntimeException("VmTool not initialized");
            }
            Method getInstancesMethod = vmTool.getClass().getMethod("getInstances", Class.class, int.class);
            return (T[]) getInstancesMethod.invoke(vmTool, klass, limit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get instances from VmTool", e);
        }
    }

    @SuppressWarnings("unused")
    protected static <T> T getBeanByClass(Class<T> klass) {
        try {
            // Use the classloader that loaded this class (which should be the app's classloader)
            ClassLoader classLoader = BaseAgentTest.class.getClassLoader();
            Class<?> applicationContextClass = Class.forName("org.springframework.context.ApplicationContext", true, classLoader);
            Object applicationContext = getVmToolInstances(applicationContextClass, 1)[0];
            Method getBean = applicationContext.getClass().getMethod("getBean", Class.class);
            return (T) getBean.invoke(applicationContext, klass);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Gets a Spring bean by class. Uses reflection to avoid ClassLoader issues.
     * For hot-reload support, this method loads classes from the app ClassLoader.
     */
    protected static Object getBeanByClassAsObject(Class<?> klass) {
        try {
            // Get the app classloader (parent of test classloader)
            ClassLoader appClassLoader = BaseAgentTest.class.getClassLoader().getParent();

            // Load ApplicationContext using app classloader
            Class<?> applicationContextClass = Class.forName("org.springframework.context.ApplicationContext", true, appClassLoader);
            Object applicationContext = getVmToolInstances(applicationContextClass, 1)[0];

            // Try to get bean by class first
            try {
                Method getBeanByClass = applicationContext.getClass().getMethod("getBean", Class.class);
                Class<?> targetClass = Class.forName(klass.getName(), true, appClassLoader);
                return getBeanByClass.invoke(applicationContext, targetClass);
            } catch (Exception e) {
                // If getBean by class fails, try by name
                String beanName = klass.getSimpleName().substring(0, 1).toLowerCase() + klass.getSimpleName().substring(1);
                Method getBeanByName = applicationContext.getClass().getMethod("getBean", String.class);
                return getBeanByName.invoke(applicationContext, beanName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invokes a method on a target object using reflection.
     * Useful for calling methods on Spring beans obtained via getBeanByClass.
     */
    protected static Object invokeMethod(Object target, String methodName, Object... args) {
        try {
            System.out.println("[BaseAgentTest] Invoking method: " + methodName + " on " + target.getClass().getName());
            System.out.println("[BaseAgentTest] Target classloader: " + target.getClass().getClassLoader());
            for (int i = 0; i < args.length; i++) {
                System.out.println("[BaseAgentTest] Arg " + i + ": " + (args[i] != null ? args[i].getClass().getName() : "null") +
                    " (classloader: " + (args[i] != null ? args[i].getClass().getClassLoader() : "null") + ")");
            }

            // Find method by name and compatible parameter count
            Method[] methods = target.getClass().getMethods();
            System.out.println("[BaseAgentTest] Total methods: " + methods.length);

            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    System.out.println("[BaseAgentTest] Found method: " + methodName + " with " + method.getParameterCount() + " params");
                    if (method.getParameterCount() == args.length) {
                        try {
                            return method.invoke(target, args);
                        } catch (IllegalArgumentException e) {
                            System.out.println("[BaseAgentTest] Parameter mismatch for method, trying next...");
                        }
                    }
                }
            }
            throw new NoSuchMethodException("Method " + methodName + " with " + args.length + " parameters not found");
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }

    /**
     * Creates an instance of a class from the app ClassLoader.
     * Use this to create parameter objects for method calls.
     * @deprecated Use createInstanceForTarget instead to ensure ClassLoader compatibility
     */
    protected static Object createInstance(String className) {
        try {
            ClassLoader appClassLoader = BaseAgentTest.class.getClassLoader().getParent();
            Class<?> clazz = Class.forName(className, true, appClassLoader);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an instance of a class using the same ClassLoader as the target object.
     * This ensures ClassLoader compatibility when passing parameters to methods.
     */
    protected static Object createInstanceForTarget(Object target, String className) {
        try {
            ClassLoader targetClassLoader = target.getClass().getClassLoader();
            Class<?> clazz = Class.forName(className, true, targetClassLoader);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets a field value using reflection.
     */
    protected static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertTrue(boolean input) {
        if (input) {
            System.out.println(formatLogMessage("PASS"));
        } else {
            throw new AssertionError(formatLogMessage("FAILED:assertTrue"));
        }
    }

    private String formatLogMessage(String s) {
        return "[Agent] " + BaseAgentTest.class.getSimpleName() + ":" + s;
    }

    // Helper to get Class from app ClassLoader
    protected Class<?> getClassFromAppClassLoader(String className) {
        try {
            return BaseAgentTest.class.getClassLoader().getParent().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== ClassLoader Debug Helpers ====================

    /**
     * Prints the ClassLoader hierarchy for debugging.
     * Usage: printClassLoaderHierarchy();
     */
    protected static void printClassLoaderHierarchy() {
        System.out.println("========== ClassLoader Hierarchy ==========");
        ClassLoader loader = BaseAgentTest.class.getClassLoader();
        int level = 0;
        while (loader != null) {
            String indent = repeatString("  ", level);
            System.out.println(indent + "Level " + level + ": " + loader.getClass()
                                                                        .getName() + "@" + System.identityHashCode(loader));
            loader = loader.getParent();
            level++;
        }
        System.out.println(repeatString("  ", level) + "Level " + level + ": Bootstrap ClassLoader (null)");
        System.out.println("==========================================");
    }

    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * Prints ClassLoader information for a given object.
     * Usage: printObjectClassLoader(someObject);
     */
    protected static void printObjectClassLoader(Object obj) {
        if (obj == null) {
            System.out.println("Object: null");
            return;
        }
        Class<?> clazz = obj.getClass();
        ClassLoader loader = clazz.getClassLoader();
        System.out.println("Object: " + clazz.getName());
        System.out.println("  ClassLoader: " + (loader != null ? loader.getClass()
                                                                       .getName() + "@" + System.identityHashCode(loader) : "Bootstrap (null)"));
    }

    /**
     * Prints detailed ClassLoader information for a class.
     * Usage: printClassLoaderInfo("com.example.SomeClass");
     */
    protected static void printClassLoaderInfo(String className) {
        try {
            ClassLoader testLoader = BaseAgentTest.class.getClassLoader();
            ClassLoader appLoader = testLoader.getParent();

            System.out.println("========== ClassLoader Info: " + className + " ==========");

            // Try to load from test ClassLoader
            try {
                Class<?> clazz = Class.forName(className, true, testLoader);
                System.out.println("From Test ClassLoader: " + clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                System.out.println("From Test ClassLoader: NOT FOUND");
            }

            // Try to load from app ClassLoader
            try {
                Class<?> clazz = Class.forName(className, true, appLoader);
                System.out.println("From App ClassLoader: " + clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                System.out.println("From App ClassLoader: NOT FOUND");
            }

            System.out.println("=================================================");
        } catch (Exception e) {
            System.out.println("Error getting ClassLoader info: " + e.getMessage());
        }
    }

    /**
     * Compares ClassLoaders of two objects.
     * Usage: compareClassLoaders(object1, object2);
     */
    protected static void compareClassLoaders(Object obj1, Object obj2) {
        System.out.println("========== ClassLoader Comparison ==========");
        printObjectClassLoader(obj1);
        printObjectClassLoader(obj2);

        ClassLoader loader1 = obj1 != null ? obj1.getClass().getClassLoader() : null;
        ClassLoader loader2 = obj2 != null ? obj2.getClass().getClassLoader() : null;

        if (Objects.equals(loader1, loader2)) {
            System.out.println("Result: SAME ClassLoader");
        } else {
            System.out.println("Result: DIFFERENT ClassLoaders!");
            System.out.println("  This may cause ClassCastException or IllegalArgumentException");
        }
        System.out.println("==========================================");
    }
}
