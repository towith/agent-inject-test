package cal.runner.junit5;

import cal.injector.Executor;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Runner5 implements InvocationInterceptor, BeforeAllCallback, Extension {
    Executor executor;

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws IOException {
        Method executable = invocationContext.getExecutable();
        Class<?> aClass = executable.getDeclaringClass();
        executor.execute(executable, aClass);
        invocation.skip();
    }

    @SuppressWarnings("ReturnOfNull")
    private RunnerConfig mergeAnnotations(List<RunnerConfig> annotations) {
        if (annotations.isEmpty()) {
            return null;
        }
        Collections.reverse(annotations);
        return annotations.get(0);
    }

    private <A extends Annotation> List<A> collectAnnotations(Class<?> clazz, Class<A> annotationType) {
        List<A> annotations = new ArrayList<>();
        Class<?> currentClass = clazz;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            System.out.printf("current class is :%s%n", currentClass);
            A annotation = currentClass.getAnnotation(annotationType);
            if (annotation != null) {
                annotations.add(annotation);
            }
            currentClass = currentClass.getSuperclass();
        }
        return annotations;
    }
    @Override
    public void beforeAll(ExtensionContext context) {
/*
        Optional<RunnerConfig> methodConfig = context.getElement().map(el -> el.getAnnotation(RunnerConfig.class));
        methodConfig.ifPresent(config -> executor = new Executor(config.appName()));
*/
        Class<?> testClass = context.getRequiredTestClass();
        List<RunnerConfig> allAnnotations = collectAnnotations(testClass, RunnerConfig.class);
        RunnerConfig mergedConfig = mergeAnnotations(allAnnotations);
        assert mergedConfig != null;
        executor = new Executor(mergedConfig.appName());
    }
}
