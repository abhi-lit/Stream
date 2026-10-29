package com.example.streamwindow;
public class RetryPolicyTest {
 static int count;
 static void check(boolean value){count++;if(!value)throw new AssertionError("Case "+count);}
 public static void main(String[] args){
  check(RetryPolicy.eligible("GET",503)); check(!RetryPolicy.eligible("POST",503));
  check(!RetryPolicy.eligible("GET",403)); check(RetryPolicy.eligible("GET",429));
  check(RetryPolicy.delay(0,null,0)==2000); check(RetryPolicy.delay(1,null,0)==4000);
  check(RetryPolicy.delay(0,"30",0)==30000); check(RetryPolicy.retryAfter("0",0)==0);
  check(RetryPolicy.retryAfter("Thu, 01 Jan 1970 00:01:00 GMT",0)==60000);
  check(RetryPolicy.retryAfter("Thu, 01 Jan 1970 00:01:00 GMT",70000)==0);
  check(RetryPolicy.retryAfter("invalid",0)==0); check(RetryPolicy.retryAfter("-1",0)==0);
  check(RetryPolicy.retryAfter("999999999999999999999999",0)==Long.MAX_VALUE);
  check(RetryPolicy.retryAfter("9223372036854775807",0)==Long.MAX_VALUE);
  check(RetryPolicy.automatic(0,2000));check(RetryPolicy.automatic(1,4000));
  check(!RetryPolicy.automatic(2,4000));check(!RetryPolicy.automatic(0,121000));
  check(RetryPolicy.automatic(1,120000));check(RetryPolicy.delay(0,"1",0)==2000);
  System.out.println(count+" retry-policy checks passed.");
 }
}
