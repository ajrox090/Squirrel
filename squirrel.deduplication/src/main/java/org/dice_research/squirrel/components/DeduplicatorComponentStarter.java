package org.dice_research.squirrel.components;

import org.dice_research.squirrel.utils.Closer;
import org.hobbit.core.components.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;

public class DeduplicatorComponentStarter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeduplicatorComponentStarter.class);

    /**
     * Exit code that is used if the program has to terminate because of an
     * internal error.
     */
    private static final int ERROR_EXIT_CODE = 1;

    private static Component component;

    private static boolean closed = false;

    private static FileSystemXmlApplicationContext context;

    /**
     * This is the main method creating and starting an instance of a
     * {@link Component} with the given class name.
     *
     * @param args The first element has to be the class name of the component.
     */
    public static void main(String[] args){
        addShutdownHook();
        LOGGER.debug("Deduplication: In main function!");
        boolean success = true;
        try {
            context = new FileSystemXmlApplicationContext(File.separator + System.getenv("CONTEXT_CONFIG_FILE"));
            component = (Component) context.getBean("deduplicatorComponent");
            // initialize the component
            component.init();
            // run the component
            component.run();
        } catch (Throwable t) {
            System.out.println("Exception while executing deduplicator component. Exiting with error code - " + t.getMessage());
            success = false;
        } finally {
            closeComponent();
        }

        if (!success) {
            System.exit(ERROR_EXIT_CODE);
        }
    }

    private static synchronized void closeComponent() {
        if (closed == false) {
            Closer.close(component, LOGGER);
            closed = true;
            context.close();
        }
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                closeComponent();
            }
        });
    }
}
