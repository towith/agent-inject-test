//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package arthas;

public interface VmToolMXBean {
    void forceGc();

    void interruptSpecialThread(int var1);

    <T> T[] getInstances(Class<T> var1);

    <T> T[] getInstances(Class<T> var1, int var2);

    long sumInstanceSize(Class<?> var1);

    long getInstanceSize(Object var1);

    long countInstances(Class<?> var1);

    Class<?>[] getAllLoadedClasses();
}
