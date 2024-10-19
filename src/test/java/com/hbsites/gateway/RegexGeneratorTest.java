package com.hbsites.gateway;

import com.hbsites.gateway.domain.RegexGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

public class RegexGeneratorTest {

    @Test
    public void regexWorksTo100() {
        int limit = 101;
        for(int i  = 1; i < limit; i++) {
            String r = RegexGenerator.getRegexRange(i);
            Pattern p = Pattern.compile(r);
            System.out.println("REGEX -> "+r+" | INDEX -> "+i);
            for (int j = 1; j < i+1; j++) {
                boolean match = p.matcher(String.valueOf(j)).find();
                System.out.println("(MATCHES "+j+" = "+(match ? "S" : "N")+")");
                Assertions.assertTrue(match);
            }
            System.out.println("--- NO MATCH ---");
            for (int j = i+1; j < limit; j++) {
                boolean match = p.matcher(String.valueOf(j)).find();
                System.out.println("(MATCHES "+j+" = "+(match ? "S" : "N")+")");
                Assertions.assertFalse(match);
            }
            System.out.println("-----------------");
        }
    }

    @Test
    public void regex46() {
        String r = RegexGenerator.getRegexRange(46);
        Pattern p = Pattern.compile(r);
        Assertions.assertTrue(p.matcher("46").find());
        Assertions.assertEquals(r, "^(([1-9])|([1-3]\\d)|(4[0-6]))$");
    }

    @Test
    public void regex32() {
        String r = RegexGenerator.getRegexRange(32);
        Pattern p = Pattern.compile(r);
        Assertions.assertTrue(p.matcher("32").find());
        Assertions.assertEquals(r, "^(([1-9])|([12]\\d)|(3[0-2]))$");
    }
}
