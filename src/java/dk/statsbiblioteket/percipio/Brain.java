package dk.statsbiblioteket.percipio;

import dk.statsbiblioteket.percipio.datastructures.BytePattern;
import dk.statsbiblioteket.percipio.datastructures.Signature;

import java.io.*;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 16, 2010
 * Time: 2:35:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class Brain {


    public static void test(File[] files, Signature signature) throws IOException {
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            boolean valid = true;
            RandomAccessFile rfile = new RandomAccessFile(file, "r");
            for (BytePattern bytePattern : signature.getFrontBlock().pattern) {
                byte[] fromFile = new byte[bytePattern.getPattern().length];
                rfile.seek(bytePattern.getOffset());
                rfile.read(fromFile);
                if (!Arrays.equals(bytePattern.getPattern(), fromFile)) {
                    valid = false;
                }
            }
            for (BytePattern bytePattern : signature.getEndBlock().pattern) {
                byte[] fromFile = new byte[bytePattern.getPattern().length];
                rfile.seek(rfile.length() - 1024 + bytePattern.getOffset());
                rfile.read(fromFile);
                if (!Arrays.equals(bytePattern.getPattern(), fromFile)) {
                    valid = false;
                }
            }
            if (valid){
                System.err.println("The file '" + file.getAbsolutePath() +"' matches the given signature");
            } else {
                System.err.println("The file '" + file.getAbsolutePath() +"' does not match the given signature");
            }
            rfile.close();
        }

    }


    public static int score(Signature signature, File file) throws IOException {
        if (!file.isFile()) {
            return 0;
        }
        int score = 0;
        int quality = signature.getGeneral().getNumberOfFiles();
        RandomAccessFile rfile = null;
        try {

            rfile = new RandomAccessFile(file, "r");
            for (BytePattern bytePattern : signature.getFrontBlock().pattern) {
                byte[] fromFile = new byte[bytePattern.getPattern().length];
                rfile.seek(bytePattern.getOffset());
                rfile.read(fromFile);
                if (Arrays.equals(bytePattern.getPattern(), fromFile)) {
                    score += fromFile.length;
                } else {
                    score -= fromFile.length;
                }
            }
            for (BytePattern bytePattern : signature.getEndBlock().pattern) {
                byte[] fromFile = new byte[bytePattern.getPattern().length];
                rfile.seek(rfile.length() - 1024 + bytePattern.getOffset());
                rfile.read(fromFile);
                if (Arrays.equals(bytePattern.getPattern(), fromFile)) {
                    score += fromFile.length;
                } else {
                    score -= fromFile.length;
                }
            }
            return quality*score;
        } finally {
            if (rfile != null){
                rfile.close();
            }
        }
    }



    public static Signature relearn(Signature signature, File... files) throws IOException {
        boolean[] headerfound = new boolean[1024];
        byte[] pattern = new byte[1024];
        //set the headerfound to the correct value
        for (int i = 0; i < headerfound.length; i++) {
            headerfound[i] = true;
        }

        for (BytePattern bytePattern : signature.getFrontBlock().pattern) {
            for (int i = 0; i < bytePattern.getPattern().length; i++) {
                headerfound[i+bytePattern.getOffset()]=false;
                pattern[i+bytePattern.getOffset()]=bytePattern.getPattern()[i];
            }
        }

        relearnFile(signature, headerfound, pattern, true,1024, files);


        //TODO rest of this method is not implemented
        boolean[] footerfound = new boolean[1024];
        byte[] patternfoot = new byte[1024];
        //set the headerfound to the correct value
        for (int i = 0; i < headerfound.length; i++) {
            headerfound[i] = true;
        }

        for (BytePattern bytePattern : signature.getEndBlock().pattern) {
            for (int i = 0; i < bytePattern.getPattern().length; i++) {
                headerfound[i+bytePattern.getOffset()]=false;
                pattern[i+bytePattern.getOffset()]=bytePattern.getPattern()[i];
            }
        }
        relearnFile(signature, footerfound, patternfoot, false,1024, files);

/*
        List<String> globalStrings = learnGlobalStrings(files);
        createSignature(header, footer);

*/
        return signature;

    }


    public static Signature learn(File... files) throws IOException {
        Signature signature = new Signature();
        signature.getGeneral().setNumberOfFiles(files.length);//TODO remove libraris and like from the list
        learnHeader(signature, files);
        learnFooter(signature, files);

/*
        List<String> globalStrings = learnGlobalStrings(files);
        createSignature(header, footer);

*/
        return signature;
    }

    private static void relearnFile(Signature signature, boolean[] found, byte[] result, boolean header, int size, File... files) throws IOException {
        result = scanFile(found, files, result,size,header);
        boolean sequence = false;
        byte[] sequenceArray = new byte[size];
        int length = 0;
        for (int i = 0; i < found.length; i++) {
            if (found[i] == false){//useful byte
                sequence = true;
                sequenceArray[length++] = result[i];
            } else {
                if (sequence){//so this is the end of a sequence
                    List<BytePattern> list;
                    if (header){
                        list = signature.getFrontBlock().pattern;
                    } else {
                        list = signature.getEndBlock().pattern;
                    }
                    list.add(new BytePattern(i-length,Arrays.copyOfRange(sequenceArray,0,length)));
                    sequence = false;
                    length = 0;
                }
            }
        }
    }


    private static void learnFooter(Signature signature, File... files) throws IOException {
        boolean[] found = new boolean[1024]; //true means the value in result does not matter. Defaults to false
        relearnFile(signature,found,null,false,1024,files);
    }

    private static void learnHeader(Signature signature, File... files) throws IOException {

        boolean[] found = new boolean[1024]; //true means the value in result does not matter. Defaults to false
        relearnFile(signature,found,null,true,1024,files);
    }

    private static byte[] scanFile( boolean[] found, File[] files, byte[] result, int size, boolean header) throws IOException {
        for (File file : files) {
            if (!file.isFile()){
                continue;
            }
            RandomAccessFile reader = new RandomAccessFile(file, "r");
            byte[] temp = new byte[size];

            if (header){
                reader.seek(0);
            } else {
                if (reader.length() > size){
                    reader.seek(reader.length()-size);
                } else {
                    continue;
                }
            }
            int length = reader.read(temp);
            reader.close();
            if (length < size){
                for (int i = length; i < found.length; i++) {
                    found[i] = true;
                }
            }
            if (result == null){
                result = temp;
            } else {
                for (int i = 0; i < temp.length; i++) {
                    if (!found[i]){
                        byte justRead = temp[i];
                        byte resultByte = result[i];
                        if (justRead != resultByte){
                            found[i] = true;
                        }
                    }
                }
            }

        }
        return result;
    }



}
