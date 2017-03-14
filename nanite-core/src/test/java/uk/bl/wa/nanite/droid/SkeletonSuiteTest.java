package uk.bl.wa.nanite.droid;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.mime.MediaType;
import org.junit.Test;

import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;

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
                if (listOfFiles[i].isFile()) {
                    MediaType fmt = dd.detect(listOfFiles[i]);
                    if (fmt.equals(MediaType.OCTET_STREAM)) {
                        fails.add("File could not be identified! "
                                + listOfFiles[i]);
                    }
                    System.out.println(
                            "File " + listOfFiles[i].getName() + " " + fmt);
                } else if (listOfFiles[i].isDirectory()) {
                    System.out.println("Directory " + listOfFiles[i].getName());
                }
            }
        }

        for (String fail : fails) {
            System.err.println("FAIL: " + fail);
        }
        // fail("Not yet implemented");
    }

}
