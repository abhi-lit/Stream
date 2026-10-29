import com.example.streamwindow.AdBlockRules;
import java.io.*;
public final class AdBlockRulesTest {
    static void check(boolean ok, String label) { if(!ok) throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        AdBlockRules rules = new AdBlockRules(new StringReader("# comment\n127.0.0.1 ads.example.com\n0.0.0.0 tracker.example.org # trailing\n127.0.0.1 localhost\n"));
        check(rules.blocksHost("ads.example.com"),"exact");
        check(rules.blocksHost("cdn.ads.example.com"),"subdomain");
        check(rules.blocksHost("ADS.EXAMPLE.COM."),"normalization");
        check(!rules.blocksHost("notads.example.com"),"boundary");
        check(!rules.blocksHost("ads.example.com.evil.org"),"suffix spoof");
        check(!rules.blocksHost(null),"null");
        check(!rules.blocksHost("example.com"),"parent remains allowed");
        check(rules.size()==2,"parse comments");
        AdBlockRules real = new AdBlockRules(new FileReader(args[0]));
        check(real.size()>3000,"real list loaded");
        check(real.blocksHost("doubleclick.net"),"known ad network");
        check(!real.blocksHost("multimovies.wtf"),"landing allowed");
        check(!real.blocksHost("m1.multimovies.courses"),"site allowed");
        System.out.println("12 checks passed; "+real.size()+" blocked domains.");
    }
}
