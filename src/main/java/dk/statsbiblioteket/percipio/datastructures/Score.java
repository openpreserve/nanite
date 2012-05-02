package dk.statsbiblioteket.percipio.datastructures;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 19, 2010
 * Time: 2:05:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class Score {
/* The purpose of this class is to hold a list score/signature pairs, in sorted order*/

    private SortedSet<Pair<Integer,Signature>> scores;
    public Score() {
        scores = new TreeSet(new Comparator<Pair<Integer,Signature>>(){

            public int compare(Pair o1, Pair o2) {
                return -o1.a.compareTo(o2.a);
            }
        });
    }

    public class Pair<A extends Comparable,B>{

        public A a;
        public B b;

        private Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }

        public A getA() {
            return a;
        }

        public B getB() {
            return b;
        }
    }

    public SortedSet<Pair<Integer,Signature>> getScoreboard(){
        return Collections.unmodifiableSortedSet(scores);
    }

    public void add(Integer score, Signature signature){
        scores.add(new Pair<Integer,Signature>(score,signature));
    }
}
