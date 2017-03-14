package uk.bl.wa.nanite.droid;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;

public class SkeletonSuiteTest {

    @Test
    public void testAll() throws CommandExecutionException {

        DroidDetector dd = new DroidDetector();

        String[] sources = { "src/test/resources/skeleton-suite-test/skeleton-suite-pronom-export-2017-03-08-sig-file-v89/fmt",
                "src/test/resources/skeleton-suite-test/skeleton-suite-pronom-export-2017-03-08-sig-file-v89/x-fmt"};

        List<String> fails = new ArrayList<String>();

        for (String source : sources) {
            File folder = new File(source);
            File[] listOfFiles = folder.listFiles();

            for (int i = 0; i < listOfFiles.length; i++) {
                File f = listOfFiles[i];
                if (f.isFile()) {

                    // Do the detection:
                    List<IdentificationResult> puids = dd
                            .detectPUIDs(f);

                    // Check for a match:
                    boolean match = false;
                    String firstPuid = null;
                    for (IdentificationResult puid : puids) {
                        if (firstPuid == null) {
                            if (puid == null) {
                                firstPuid = "NULL!";
                            } else {
                                firstPuid = puid.getPuid();
                            }
                        }
                        if (puid == null) {
                            System.out.println("NULL!!!: " + f);
                            continue;
                        }
                        // Compare:
                        String puidPrefix = puid.getPuid().replaceAll("/", "-");
                        if (f.getName().startsWith(puidPrefix)) {
                            match = true;
                        }
                    }

                    // Store result:
                    if (!match) {
                        fails.add("File could not be identified! "
                                + f.getName()+ " : got "+firstPuid);
                    }
                }
            }
        }

        for (String fail : fails) {
            System.out.println("FAIL: " + fail);
        }
        // fail("Not yet implemented");
    }

}
