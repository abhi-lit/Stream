package com.example.streamwindow;
import java.util.*;
import java.io.*;

public final class AdBlockRules {
    private final Set<String> hosts;
    public AdBlockRules(Reader input) throws IOException {
        Set<String> parsed = new HashSet<>();
        BufferedReader lines = new BufferedReader(input);
        String line;
        while ((line = lines.readLine()) != null) {
            line = line.split("#", 2)[0].trim();
            String[] fields = line.split("\\s+");
            if (fields.length < 2 || !(fields[0].equals("127.0.0.1") || fields[0].equals("0.0.0.0"))) continue;
            for (int i=1; i<fields.length; i++) {
                String host = fields[i].toLowerCase(Locale.ROOT);
                if (host.indexOf('.') > 0 && !host.equals("localhost")) parsed.add(host);
            }
        }
        hosts = Collections.unmodifiableSet(parsed);
    }
    public boolean blocksHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0,host.length()-1);
        while (!host.isEmpty()) {
            if (hosts.contains(host)) return true;
            int dot = host.indexOf('.');
            if (dot < 0) return false;
            host = host.substring(dot+1);
        }
        return false;
    }
    public int size() { return hosts.size(); }
}
