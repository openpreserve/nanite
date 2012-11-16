package dk.statsbiblioteket.percipio;

import dk.statsbiblioteket.percipio.datastructures.BytePattern;
import dk.statsbiblioteket.percipio.datastructures.Score;
import dk.statsbiblioteket.percipio.datastructures.Signature;

import java.io.*;
import java.util.*;

/**
 * This is the thinking class of Percipio. Here all the calculations are performed
 */
public class Brain {

    public static final int SIZE = 1024;


    public Brain() {
    }


    public void test(List<File> files, Signature signature) throws IOException{
        test(files.toArray(new File[files.size()]),signature);
    }

    public void test(File[] files, Signature signature) throws IOException {
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
                long seekOffset = rfile.length() - SIZE + bytePattern.getOffset();
                if( seekOffset >= 0 ) {
                	rfile.seek(seekOffset);
                	rfile.read(fromFile);
                	if (!Arrays.equals(bytePattern.getPattern(), fromFile)) {
                		valid = false;
                	}
                } else {
                	valid = false;
                }
            }
            if (valid) {
                System.err.println("The file '" + file.getAbsolutePath() + "' matches the given signature");
            } else {
                System.err.println("The file '" + file.getAbsolutePath() + "' does not match the given signature");
            }
            rfile.close();
        }

    }


    public Map<File, Score> score(List<Signature> signatures, File... files) throws IOException {
        return score(signatures, Arrays.asList(files));
    }


    public Map<File, Score> score(List<Signature> signatures, List<File> files) throws IOException {
        Map<File, Score> scores = new HashMap<File,Score>();

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            Score score = score(signatures,file);
            scores.put(file,score);
        }
        return scores;

    }

    public Score score(List<Signature> signatures, File file) throws IOException {

        Score score = new Score();
        for (Signature signature : signatures) {

            int tempscore = 0;
            int quality = signature.getGeneral().getNumberOfFiles();
            RandomAccessFile rfile = null;
            try {

                rfile = new RandomAccessFile(file, "r");
                for (BytePattern bytePattern : signature.getFrontBlock().pattern) {
                    byte[] fromFile = new byte[bytePattern.getPattern().length];
                    rfile.seek(bytePattern.getOffset());
                    rfile.read(fromFile);
                    if (Arrays.equals(bytePattern.getPattern(), fromFile)) {
                        tempscore += fromFile.length;
                    } else {
                        tempscore -= fromFile.length;
                    }
                }
                for (BytePattern bytePattern : signature.getEndBlock().pattern) {
                    byte[] fromFile = new byte[bytePattern.getPattern().length];
                    rfile.seek(rfile.length() - SIZE + bytePattern.getOffset());
                    rfile.read(fromFile);
                    if (Arrays.equals(bytePattern.getPattern(), fromFile)) {
                        tempscore += fromFile.length;
                    } else {
                        tempscore -= fromFile.length;
                    }
                }
                score.add(quality * tempscore,signature);
            } finally {
                if (rfile != null) {
                    rfile.close();
                }
            }

        }
        return score;
    }

    public Signature relearn(Signature signature, List<File> files) throws IOException {
        return relearn(signature,files.toArray(new File[files.size()]));
    }

    /**
     * Update the signature, so that it matches all the provided files, but still matches everything it matched before
     *
     * @param signature the signature to update. Will be updated inline.
     * @param files     the files to base the update on
     * @return the signature updated.
     * @throws IOException on file reading errors
     * @see #learn(java.util.List)
     */
    public Signature relearn(Signature signature, File... files) throws IOException {

        //First, we look at the header
        //first, parse the signature to the result of an earlier "learn"
        boolean[] headerfound = new boolean[SIZE];
        byte[] patternhead = new byte[headerfound.length];
        for (int i = 0; i < headerfound.length; i++) {
            headerfound[i] = true;
        }
        for (BytePattern bytePattern : signature.getFrontBlock().pattern) {
            for (int i = 0; i < bytePattern.getPattern().length; i++) {
                headerfound[i + bytePattern.getOffset()] = false;
                patternhead[i + bytePattern.getOffset()] = bytePattern.getPattern()[i];
            }
        }
        //Then attempt to continue the learning, from the old point
        relearnFile(signature, headerfound, patternhead, true, files);

        //Then do the same for the footer

        boolean[] footerfound = new boolean[SIZE];
        byte[] patternfoot = new byte[footerfound.length];

        for (int i = 0; i < footerfound.length; i++) {
            footerfound[i] = true;
        }

        for (BytePattern bytePattern : signature.getEndBlock().pattern) {
            for (int i = 0; i < bytePattern.getPattern().length; i++) {
                footerfound[i + bytePattern.getOffset()] = false;
                patternfoot[i + bytePattern.getOffset()] = bytePattern.getPattern()[i];
            }
        }
        //attempt to continue learning from the old point, but now for the footer
        relearnFile(signature, footerfound, patternfoot, false, files);

        signature.getGeneral().setNumberOfFiles(signature.getGeneral().getNumberOfFiles() + files.length);

        return signature;
    }

    /**
     * Construct a new signature based on the list of files provided.
     *
     * @param files The files to construct the signature from. Must all be "real" files, not directories or special files
     * @return the new Signature
     * @throws IOException if an error occurred when reading the files.
     * @see #learn(java.io.File...)
     */
    public Signature learn(List<File> files) throws IOException {
        return learn(files.toArray(new File[files.size()]));
    }

    /**
     * Construct a new signature based on the provided files
     *
     * @param files the files to use
     * @return the new Signature
     * @throws IOException if an error occurred when reading the files.
     * @see #learn(java.util.List)
     */
    public Signature learn(File... files) throws IOException {
        Signature signature = new Signature();
        signature.getGeneral().setNumberOfFiles(files.length);

        boolean[] foundBytesInHeader = new boolean[SIZE]; //true means the value in result does not matter. Defaults to false
        relearnFile(signature, foundBytesInHeader, null, true,  files);

        boolean[] foundBytesInTrailer = new boolean[SIZE]; //true means the value in result does not matter. Defaults to false
        relearnFile(signature, foundBytesInTrailer, null, false, files);
        return signature;
    }


    /**
     * This is the method used for discovering and parsing the signatures in the files
     *
     * @param signature the signature to work on
     * @param found     the byte locations that have already been found. True denote that the byte is not to be used in the
     *                  signature, as one of the files does not match. Should all be false, if no learning has taken place already
     * @param result    The result of an earlier run. Should be null, if no earlier run.
     * @param header    Should the header or footer be examined. True means header
     * @param files     the files to examine
     * @throws IOException if file reading fails.
     */
    private void relearnFile(Signature signature, boolean[] found, byte[] result, boolean header,
                             File... files) throws IOException {
        //Scan the files, and find matches. Result and found is updated inline
        result = scanFile(found, files, result, header);
        if( result == null ) return;

        //Scan for sequences, to properly write the signature
        boolean sequence = false;
        byte[] sequenceArray = new byte[result.length];
        int length = 0;
        for (int i = 0; i < found.length; i++) {
            if (found[i] == false) {//useful byte
                sequence = true;
                sequenceArray[length++] = result[i];
            } else {
                if (sequence) {//so this is the end of a sequence
                    List<BytePattern> list;
                    if (header) {
                        list = signature.getFrontBlock().pattern;
                    } else {
                        list = signature.getEndBlock().pattern;
                    }
                    list.add(new BytePattern(i - length, Arrays.copyOfRange(sequenceArray, 0, length)));
                    sequence = false;
                    length = 0;
                }
            }
        }
    }


    /**
     * Scan the files, and find matches in the header or trailer. As the scanning goes on, the "found" array is updated.
     * It denotes which locations are identical in all the files scanned so far. The "result" array denote the values
     * read, that match all files. Values in locations that are not "false" in "found" should be disregarded.
     * @param found the array of which locations match.
     * @param files the files to scan
     * @param result the result array of bytes
     * @param header if the header or footer should be scanned
     * @return the result array, which is not useful without the found array. Both have been updated by reference during
     * the run
     * @throws IOException If the file reading failed.
     */
    private byte[] scanFile(boolean[] found, File[] files, byte[] result, boolean header) throws IOException {
        int size = found.length;
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            RandomAccessFile reader = new RandomAccessFile(file, "r");
            byte[] temp = new byte[size];

            if (header) {
                reader.seek(0);
            } else {
                if (reader.length() > size) {
                    reader.seek(reader.length() - size);
                } else {
                    continue;
                }
            }
            int length = reader.read(temp);
            reader.close();
            if (length < size) {//if we could not read enough. Not likely to happen
                for (int i = length; i < found.length; i++) {
                    found[i] = true;
                }
            }
            if (result == null) {
                result = temp;
            } else {
                for (int i = 0; i < temp.length; i++) {
                    if (!found[i]) {
                        byte justRead = temp[i];
                        byte resultByte = result[i];
                        if (justRead != resultByte) {
                            found[i] = true;
                        }
                    }
                }
            }

        }
        return result;
    }


}
