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
 * <p>
 * <p>参考文档：http://verify-email.org/</p>
 * <p>
 * Created by zhouyang.zhou.
 */
public class EmailValidator {

  private static final String EMAIL_REGEX = "[\\w\\.\\-]+@([\\w\\-]+\\.)+[\\w\\-]+";
  private static final Pattern PATTERN = Pattern.compile(EMAIL_REGEX);
  private static final Splitter SPLITTER_AT = Splitter.on("@").trimResults();

  private SMTPClient smtpClient;
  private String emailAddress;
  private String emailServer;

  public EmailValidator() {
    this.smtpClient = new SMTPClient();
  }

  public boolean validate(String emailAddress) {
    try {
      this.emailAddress = emailAddress;

      //validate the email address format
      validateFormat(emailAddress);

      //get the email server address
      String emailServer = getEmailServer();

      //lookup mx records
      Record[] records = lookupMX();

      //validate the mx records
      validateMX(records);

      //try acquire the email connection,send a signal to test...
      boolean acquire = tryAcquire();
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

  private String getEmailServer() {
    Iterable<String> split = SPLITTER_AT.split(emailAddress);
    Iterator<String> iterator = split.iterator();
    iterator.next();
    String emailServer = iterator.next();
    this.emailServer = emailServer;
    return emailServer;
  }

  private void validateFormat(String emailAddress) {
    Preconditions.checkNotNull(emailAddress, "the email address is empty");
    Matcher matcher = PATTERN.matcher(emailAddress);
    if (!matcher.find()) {
      throw new RuntimeException(String.format("the email address format invalid,emailAddress=%s", emailAddress));
    }
  }

  private Record[] lookupMX() throws TextParseException {
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
        System.out.println(String.format("MX record about %s exists.", emailServer));
        System.out.println(String.format("Connection succeeded to %s SMTP.", validateServer));
        System.out.println(smtpClient.getReplyString());

        break;
      }
    }
    if (Strings.isNullOrEmpty(validateServer)) {
      throw new RuntimeException(String.format("can not find the valid mx"));
    }
  }

  private boolean tryAcquire() throws IOException {
    String fromAddress = "glamey.zhou@gmail.com";
    String fromServer = "gmail.com";
    System.out.println(String.format("> HELO %s", fromServer));
    smtpClient.login(fromServer);
    System.out.println(smtpClient.getReplyString());

    System.out.println("> MAIL FROM <" + fromAddress + ">");
    smtpClient.setSender(fromAddress);
    System.out.println("=" + smtpClient.getReplyString());

    System.out.println("> RCPT TO <" + emailAddress + ">");
    smtpClient.addRecipient(emailAddress);
    System.out.println("=" + smtpClient.getReplyString());

    int replyCode = smtpClient.getReplyCode();
    return SMTPReply.ACTION_OK == replyCode;
  }

  public static void main(String[] args) throws Exception {
    if (args == null || args.length != 1) {
      System.out.println("参数格式为 java -jar email-validator.jar [emailAddress]");
      return;
    }
    System.out.println(new EmailValidator().validate(args[0]));
  }
}
