package com.capdi.backend.domain.payment.config;

import org.springframework.core.env.Environment;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Optional read-only secret mount. Never log paths, supplied values, or decoder/I/O causes. */
final class PortOneSecretFiles {
    private PortOneSecretFiles() { }
    static String resolve(Environment env, String name) {
        String inline=env.getProperty("payment.portone."+name);
        String file=env.getProperty("payment.portone."+name+"-file");
        if(file==null)return inline; // Existing inline configuration remains compatible.
        try {
            if(inline!=null && !inline.isEmpty())throw new IllegalArgumentException();
            Path path=Path.of(file);
            if(!path.isAbsolute() || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException();
            byte[] bytes;
            try(var in=Files.newInputStream(path)) {bytes=in.readNBytes(4099);}
            if(bytes.length==0 || bytes.length>4098)throw new IllegalArgumentException();
            String value=new String(bytes,StandardCharsets.US_ASCII);
            java.util.Arrays.fill(bytes,(byte)0);
            if(value.endsWith("\r\n"))value=value.substring(0,value.length()-2);
            else if(value.endsWith("\n"))value=value.substring(0,value.length()-1);
            if(value.isEmpty() || value.length()>4096 || value.chars().anyMatch(c->c<33 || c>126))
                throw new IllegalArgumentException();
            return value;
        } catch(Exception ignored) {
            throw new IllegalArgumentException("PortOne secret file configuration invalid");
        }
    }
}
