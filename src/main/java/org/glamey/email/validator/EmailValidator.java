package org.glamey.email.validator;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>校验邮箱地址是否符合要求</p>
 * Created by zhouyang.zhou.
 */
public class EmailValidator {

  private static final String EMAIL_REGEX = "[\\w\\.\\-]+@([\\w\\-]+\\.)+[\\w\\-]+";
  private static final Pattern PATTERN = Pattern.compile(EMAIL_REGEX);
  private static final Splitter SPLITTER_AT = Splitter.on("@").trimResults();

  private SMTPClient smtpClient;

  public EmailValidator() {
    this.smtpClient = new SMTPClient();
  }

  public boolean validate(String emailAddress) {
    try {
      //validate the email address format
      validateFormat(emailAddress);

      //get the email server address
      String emailServer = getEmailServer(emailAddress);

      //lookup mx records
      Record[] records = lookupMX(emailServer);

      //validate the mx records
      validateMX(records);

      //try acquire the email connection,send a signal to test...
      boolean acquire = tryAcquire(emailAddress);
      System.out.println(String.format("--> [%s] is %s", emailAddress, acquire ? "valid" : "invalid"));
      return acquire;

    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        smtpClient.disconnect();
      } catch (IOException e) {
        //ignore
      }
    }
    return false;
  }

  private String getEmailServer(String emailAddress) {
    Iterable<String> split = SPLITTER_AT.split(emailAddress);
    Iterator<String> iterator = split.iterator();
    iterator.next();
    String emailServer = iterator.next();
    return emailServer;
  }

  private void validateFormat(String emailAddress) {
    Preconditions.checkNotNull(emailAddress, "the email address is empty");
    Matcher matcher = PATTERN.matcher(emailAddress);
    if (!matcher.find()) {
      throw new RuntimeException(String.format("the email address format invalid,emailAddress=%s", emailAddress));
    }
  }

  private Record[] lookupMX(String emailServer) throws TextParseException {
    Record[] records;
    // 查找MX记录
    Lookup lookup = new Lookup(emailServer, Type.MX);
    lookup.run();
    if (lookup.getResult() != Lookup.SUCCESSFUL) {
      throw new RuntimeException(String.format("lookup MX error,hostName=%s", emailServer));
    } else {
      records = lookup.getAnswers();
    }
    return records;
  }

  private void validateMX(Record[] records) throws IOException {
    String validateServer = null;
    for (Record record : records) {
      String host = record.getAdditionalName().toString();
      smtpClient.connect(host);
      if (SMTPReply.isPositiveCompletion(smtpClient.getReplyCode())) {
        //验证OK
        validateServer = host;
        System.out.println("--> Connection [" + host + "] Success... ");
        System.out.println("-->" + smtpClient.getReplyString());

        break;
      }
    }
    if (Strings.isNullOrEmpty(validateServer)) {
      throw new RuntimeException(String.format("can not find the valid mx"));
    }
  }

  private boolean tryAcquire(String emailAddress) throws IOException {
    String fromAddress = "glamey.zhou@gmail.com";
    System.out.println("HELO qq.com");
    smtpClient.login("gmail.com");
    System.out.println("-->" + smtpClient.getReplyString());

    System.out.println("MAIL FROM <" + fromAddress + ">");
    smtpClient.setSender(fromAddress);
    System.out.println("-->" + smtpClient.getReplyString());

    System.out.println("RECIPIENT <" + emailAddress + ">");
    smtpClient.addRecipient(emailAddress);
    System.out.println("-->" + smtpClient.getReplyString());

    int replyCode = smtpClient.getReplyCode();
    return SMTPReply.ACTION_OK == replyCode;
  }

  public static void main(String[] args) throws Exception {
   /* if (args == null || args.length != 1) {
      System.out.println("参数格式为 java -jar email-validator.jar [emailAddress]");
      return;
    }
    System.out.println(new EmailValidator().validate(args[0]));*/

    new EmailValidator().validate("shangguanhong@b-ray.com.cn");
    new EmailValidator().validate("qingyun.song@bossfounder.com.cn");
  }
}
