package uk.bl.wa.nanite.droid;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.internal.api.ApiResultExtended;

/**
 * 
 * The suite needs updating for every PRONOM release. 
 * 
 * nanite-core/src/test/resources/skeleton-suite
 * 
 * How to update this test suite is outlined here: https://github.com/exponential-decay/skeleton-test-suite-generator#howto
 * 
 * We use the pronom-export Python script (https://github.com/exponential-decay/pronom-xml-export) 
 * to download the PRONOM XML into a folder with the name pronom-export in the skeleton-test-suite-generator repo
 * 
 * Then we run the skeleton generator.
 * 
 * Then replace nanite-core/src/test/resources/skeleton-suite with that new skeleton-suite
 * 
 * @author anj
 *
 */
public class SkeletonSuiteTest {

    @Test
    public void testSkeletonSuite() throws IOException, SignatureParseException  {

        DroidDetector dd = new DroidDetector();

        String[] sources = { "src/test/resources/skeleton-suite/fmt",
                "src/test/resources/skeleton-suite/x-fmt" };

        List<String> fails = new ArrayList<String>();

        // For all sources:
        for (String source : sources) {
            File folder = new File(source);
            File[] listOfFiles = folder.listFiles();

            // For all giles:
            for (int i = 0; i < listOfFiles.length; i++) {
                File f = listOfFiles[i];
                if (f.isFile()) {

                    // Do the detection:
                    List<ApiResultExtended> puids = dd
                            .identify(f);

                    // Check for a match:
                    boolean match = false;
                    String firstPuid = null;
                    for (ApiResultExtended puid : puids) {
                        // Record the first (potentially mis-matched) PUID for
                        // reporting purposes:
                        if (firstPuid == null) {
                            if (puid == null) {
                                firstPuid = "NULL!";
                            } else {
                                firstPuid = puid.getPuid();
                            }
                        }
                        // Skip any further processing if a NULL is returned:
                        if (puid == null) {
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
        // Check this is as expected:
        if (fails.size() > 4) {
            fail("More errors than expected when running against the Skeleton Suite! Total = "
                    + fails.size());
        }
    }

}
