package com.capdi.backend.domain.payment.service;
import com.capdi.backend.global.exception.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** AES-GCM with versioned key ID and context binding. No secret or payload in error messages. */
public final class FinancialVault {
 private final Map<String,byte[]> keys;private final String active;private final SecureRandom random=new SecureRandom();
 public FinancialVault(String active,Map<String,byte[]> keys) {
  if(active==null || !active.matches("[A-Za-z0-9_-]{1,30}"))throw new IllegalArgumentException("Invalid key identifier");
  this.active=active;this.keys=new HashMap<>();
  keys.forEach((k,v)->{if(v.length!=32)throw new IllegalArgumentException("AES-256 required");this.keys.put(k,v.clone());});
 }
 public boolean available(){return keys.containsKey(active);}
 public String seal(String context,String text){
  try {
   if(!available())throw new IllegalStateException();byte[] nonce=new byte[12];random.nextBytes(nonce);
   Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(active),"AES"),new GCMParameterSpec(128,nonce));
   c.updateAAD(context.getBytes(StandardCharsets.UTF_8));byte[] encrypted=c.doFinal(text.getBytes(StandardCharsets.UTF_8));
   return active+":"+Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(encrypted);
  }catch(Exception e){throw unavailable();}
 }
 public String open(String context,String text){
  try {
   String[] p=text.split(":",-1);if(p.length!=3 || !keys.containsKey(p[0]))throw new IllegalStateException();
   byte[] iv=Base64.getDecoder().decode(p[1]);if(iv.length!=12)throw new IllegalStateException();
   Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keys.get(p[0]),"AES"),new GCMParameterSpec(128,iv));
   c.updateAAD(context.getBytes(StandardCharsets.UTF_8));return new String(c.doFinal(Base64.getDecoder().decode(p[2])),StandardCharsets.UTF_8);
  }catch(Exception e){throw unavailable();}
 }
 public String fingerprint(String value) {
  try {if(!available())throw new IllegalStateException();Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(keys.get(active),"HmacSHA256"));
   return HexFormat.of().formatHex(m.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  }catch(Exception e){throw unavailable();}
 }
 public boolean matchesFingerprint(String ciphertext,String raw,String expected) {
  try {
   String id=ciphertext.split(":",2)[0];if(!keys.containsKey(id))return false;
   Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(keys.get(id),"HmacSHA256"));
   return MessageDigest.isEqual(m.doFinal(raw.getBytes(StandardCharsets.UTF_8)),HexFormat.of().parseHex(expected));
  }catch(Exception e){return false;}
 }
 private CustomException unavailable(){return new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);}
 @Override public String toString(){return "FinancialVault[REDACTED]";}
}
