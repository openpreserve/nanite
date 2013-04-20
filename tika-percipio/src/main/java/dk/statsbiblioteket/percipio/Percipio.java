package dk.statsbiblioteket.percipio;

import dk.statsbiblioteket.percipio.Brain;
import dk.statsbiblioteket.percipio.datastructures.BytePattern;
import dk.statsbiblioteket.percipio.datastructures.Score;
import dk.statsbiblioteket.percipio.datastructures.Signature;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 16, 2010
 * Time: 3:51:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class Percipio {

    public static void main(String... args) throws IOException, JAXBException {

        JAXBContext context = JAXBContext.newInstance(Signature.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE );
        Unmarshaller unmarshaller = context.createUnmarshaller();
        
        if( args.length == 0 || "-h".equals(args[0]) ){
        	System.out.println("percipio (learn|relearn|sniff) [-s SigFileName] [-n NumberOfMatches] [-M] filenamess...");
        	return;
        }

        String command = args[0];

        String signatureArg = "";
        int numberOfMatchesArg = 5;
        ArrayList<File> files = new ArrayList<File>();
        boolean useMimeInfoFormat = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-s")){
                i++;
                signatureArg = args[i];
                continue;
            }
            if (arg.equals("-n")){
                i++;
                numberOfMatchesArg = new Integer(args[i]);
                continue;
            }
            if (arg.equals("-M") ) {
            	useMimeInfoFormat = true;
            }

            File file = new File(arg);
            if (file.isFile()){//TODO recursion
                files.add(file);
            }
        }



        Brain brain = new Brain();

        if (command.equals("learn")){
            Signature signature = brain.learn(files);
            brain.test(files,signature);
            
            if( useMimeInfoFormat ) {
            	Percipio.printMimeInfoSignature(signature);
            } else {
            	StringWriter writer = new StringWriter();
            	marshaller.marshal(signature,writer);
            	System.out.println(writer.toString());
            }
        }
        if (command.equals("relearn")){
            Signature signature = (Signature)unmarshaller.unmarshal(System.in);
            signature = brain.relearn(signature,files);
            brain.test(files,signature);

            StringWriter writer = new StringWriter();
            marshaller.marshal(signature,writer);
            System.out.println(writer.toString());
        }
        if (command.equals("sniff")){
        	System.out.println("signatureArg: "+signatureArg);
            List<Signature> signatures = parseSignatures(unmarshaller, signatureArg);

            Map<File, Score> scores = brain.score(signatures,files);
            System.out.println("size() = "+scores.size());
            for (File file : scores.keySet()) {
                printScores(file,scores.get(file), numberOfMatchesArg);
            }
            

        }


    }

    private static void printMimeInfoSignature(Signature signature) {
    	System.out.println("<?xml version=\"1.0\"?>");
    	System.out.println("<mime-info>");
    	System.out.println(" <mime-type type=\"application/x-unknown\">");
    	System.out.println("  <acronym></acronym>");
    	System.out.println("  <_comment></_comment>");
    	System.out.println("  <magic priority=\"50\">");
    	String indent = "  ";
    	for( BytePattern p : signature.getFrontBlock().pattern) {
    		indent += "  ";
    		System.out.println(indent+"<!-- ASCII: "+p.getAscii()+" @"+p.getOffset()+" -->");
    		System.out.println(indent+"<match value=\"0x"+p.getBytes()+"\" type=\"string\" offset=\""+p.getOffset()+"\">");
    	}
    	for( BytePattern p : signature.getFrontBlock().pattern) {
    		System.out.println(indent+"</match>");
    		indent = indent.substring(2);
    	}
        System.out.println("  </magic>");
        System.out.println("  <glob pattern=\"*.???\"/>");
        System.out.println(" </mime-type>");
        System.out.println("</mime-info>");
	}

	private static void printScores(File file, Score score, int numberOfMatches) {
        int total = 0;
        for (Score.Pair<Integer, Signature> integerSignaturePair : score.getScoreboard()) {
            total += integerSignaturePair.getA();
        }
        int prints = 0;
        for (Score.Pair<Integer, Signature> integerSignaturePair : score.getScoreboard()) {
            String message = integerSignaturePair.getB().getInfo().getFileType() + ": " +
                             ((int)((integerSignaturePair.getA()+0.0)*10000 / total)+0.0)/100 + "%";
            System.out.println(message);
            prints++;
            if (prints > numberOfMatches){
                break;
            }
        }
    }


    private static List<Signature> parseSignatures(Unmarshaller unmarshaller, String signatureArg)
            throws JAXBException {
        File signatureFolder = new File(signatureArg);
        List<Signature> signatures = new ArrayList<Signature>();
        if (signatureFolder.isDirectory()){
            File[] signatureFiles = signatureFolder.listFiles(new FilenameFilter() {

                public boolean accept(File dir, String name) {
                    if (name.endsWith(".sig") || name.endsWith(".xml")) {
                        return true;
                    }
                    return false;
                }
            });

            for (File signatureFile : signatureFiles) {
                Signature signature = (Signature)unmarshaller.unmarshal(signatureFile);
                signatures.add(signature);
            }
        }
        return signatures;
    }

}
