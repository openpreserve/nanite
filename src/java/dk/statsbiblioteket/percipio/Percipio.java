package dk.statsbiblioteket.percipio;

import dk.statsbiblioteket.percipio.Brain;
import dk.statsbiblioteket.percipio.datastructures.Signature;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;

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

        String command = args[0];

        ArrayList<File> files = new ArrayList<File>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            File file = new File(arg);
            if (file.isDirectory()){
                files.addAll(Arrays.asList(file.listFiles()));
            } else{
                files.add(file);
            }
        }

        if (command.equals("learn")){
            Signature signature = Brain.learn(files.toArray(new File[0]));
            Brain.test(files.toArray(new File[0]),signature);

            StringWriter writer = new StringWriter();
            marshaller.marshal(signature,writer);
            System.out.println(writer.toString());
        }

        if (command.equals("sniff")){
            Signature signature = (Signature)unmarshaller.unmarshal(System.in);
            Brain.test(files.toArray(new File[0]),signature);
        }

        if (command.equals("relearn")){
            //signatures
            Signature signature = (Signature)unmarshaller.unmarshal(System.in);

            signature = Brain.relearn(signature,files.toArray(new File[0]));
            Brain.test(files.toArray(new File[0]),signature);

            StringWriter writer = new StringWriter();
            marshaller.marshal(signature,writer);
            System.out.println(writer.toString());
        }

    }

}
