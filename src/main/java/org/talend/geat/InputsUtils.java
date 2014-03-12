package org.talend.geat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class InputsUtils {

    public static String askUser(String question) throws IOException {
        return askUser(question, "");
    }

    public static String askUser(String question, String defaultValue) throws IOException {
        if (question != null) {
            System.out.print(question);
        }
        if (defaultValue != null && defaultValue.length() > 0) {
            System.out.print(" [" + defaultValue + "]");
        }
        System.out.println(" ? ");

        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);

        String str = br.readLine();
        if (str == null || str.length() < 1) {
            return defaultValue;
        } else {
            return str;
        }
    }

}