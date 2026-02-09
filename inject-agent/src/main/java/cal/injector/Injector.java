package cal.injector;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;

public class Injector {
    @SuppressWarnings({"CallToSystemExit", "JvmTaintAnalysis"})
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: Injector <PID> <TestClassName> <DependenciesDir>");
            System.exit(1);
        }

        String pid = args[0];
        String testClass = args[1];
        String depDir = args[2];
        String agentPath = args[3];

        // Try to find the agent jar relative to the run location
        File agentJar = new File(agentPath);
        if (!agentJar.exists()) {
            // Fallback: try absolute path if we assume a fixed install location,
            // but strictly we should pass it or expect it in CWD.
            // Let's try to look in the target dir relative to where we might be running this from.
            // If this tool is mapped to scratch, let's look there.
            throw new RuntimeException(String.format("angent path not exists:%s", agentJar));
        }

        if (!agentJar.exists()) {
            System.err.println("Could not find agent jar. Searched at: " + agentJar.getAbsolutePath());
            System.exit(1);
        }

        try {
            System.out.println("Attaching to process " + pid);
            VirtualMachine vm = VirtualMachine.attach(pid);

            String agentArgs = testClass + "|" + new File(depDir).getAbsolutePath();

            System.out.println("Loading agent from: " + agentJar.getAbsolutePath());
            System.out.println("Agent args: " + agentArgs);

            vm.loadAgent(agentJar.getAbsolutePath(), agentArgs);

            vm.detach();
            System.out.println("Agent loaded successfully. Check target process stdout.");

        } catch (Exception e) {
            System.err.println("Error attaching/loading agent:");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
