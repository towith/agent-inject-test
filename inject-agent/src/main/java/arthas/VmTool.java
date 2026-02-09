//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package arthas;

import java.util.Map;

public class VmTool implements VmToolMXBean {
    public static final String JNI_LIBRARY_NAME = "ArthasJniLibrary";
    private static VmTool instance;

    private VmTool() {
    }

    public static VmTool getInstance() {
        return getInstance((String)null);
    }

    public static synchronized VmTool getInstance(String libPath) {
        if (instance != null) {
            return instance;
        } else {
            if (libPath == null) {
                System.loadLibrary("ArthasJniLibrary");
            } else {
                System.load(libPath);
            }

            instance = new VmTool();
            return instance;
        }
    }

    private static synchronized native void forceGc0();

    private static synchronized native <T> T[] getInstances0(Class<T> var0, int var1);

    private static synchronized native long sumInstanceSize0(Class<?> var0);

    private static native long getInstanceSize0(Object var0);

    private static synchronized native long countInstances0(Class<?> var0);

    private static synchronized native Class<?>[] getAllLoadedClasses0(Class<?> var0);

    public void forceGc() {
        forceGc0();
    }

    public void interruptSpecialThread(int threadId) {
        Map<Thread, StackTraceElement[]> allThread = Thread.getAllStackTraces();

        for(Map.Entry<Thread, StackTraceElement[]> entry : allThread.entrySet()) {
            if (((Thread)entry.getKey()).getId() == (long)threadId) {
                ((Thread)entry.getKey()).interrupt();
                return;
            }
        }

    }

    public <T> T[] getInstances(Class<T> klass) {
        return (T[])getInstances0(klass, -1);
    }

    public <T> T[] getInstances(Class<T> klass, int limit) {
        if (limit == 0) {
            throw new IllegalArgumentException("limit can not be 0");
        } else {
            return (T[])getInstances0(klass, limit);
        }
    }

    public long sumInstanceSize(Class<?> klass) {
        return sumInstanceSize0(klass);
    }

    public long getInstanceSize(Object instance) {
        return getInstanceSize0(instance);
    }

    public long countInstances(Class<?> klass) {
        return countInstances0(klass);
    }

    public Class<?>[] getAllLoadedClasses() {
        return getAllLoadedClasses0(Class.class);
    }
}
