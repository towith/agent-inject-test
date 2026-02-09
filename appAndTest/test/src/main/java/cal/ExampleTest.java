package cal;

import com.example.demo.controller.HelloController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ExampleTest extends BaseTest {

    @Test
    @DisplayName("class loader issue")
    void testClassLoader() {
        System.out.println("this is hot reload");
        compareClassLoaders(this, new ExampleTest());
//        getBeanByClass()
        printClassLoaderInfo("org.springframework.context.ApplicationContext");
        printObjectClassLoader(this);
    }

    @Test
    @DisplayName("print target application object invoke and value")
    void testGetTargetApplicationObjects() {
        HelloController beanByClass = getBeanByClass(HelloController.class);
        System.out.printf("got bean:%s%n", beanByClass);
        System.out.println(beanByClass.getTestField());
        beanByClass.hello();
//        System.out.println("hot reloaded");
    }
}
