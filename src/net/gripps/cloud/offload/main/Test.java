package net.gripps.cloud.offload.main;

/**
 * Created by kanem on 2018/11/14.
 */
public class Test {
    public static void main(String[] args){

        double lambda = 0.1;
        /*double tau = -1.0 / lambda * (1.0 - Math.random());*/
        double tau =3;

        double f = lambda *  Math.pow(Math.E, (-1)*tau*lambda);
        System.out.println("F: "+f);

        //累積
        double cf = 1- Math.pow(Math.E, (-1)*tau*lambda);
        System.out.println("CF: "+ cf);
/**
        PoissonDistributionImpl dist = new PoissonDistributionImpl(4);
        System.out.println("kakurituは"+dist.probability(5));
**/
    }
}
