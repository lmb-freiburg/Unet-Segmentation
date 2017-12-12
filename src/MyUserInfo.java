import com.jcraft.jsch.*;

import javax.swing.*;

public class MyUserInfo implements UserInfo, UIKeyboardInteractive {

  public boolean promptYesNo(String message) {
    Object[] options={ "yes", "no" };
    int foo=JOptionPane.showOptionDialog(
        null, message, "Warning",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.WARNING_MESSAGE, null, options,
        options[0]);
    return foo==0;
  }

  public void showMessage(String message) {
    JOptionPane.showMessageDialog(null, message);
  }

  public String getPassword() {
    return null;
  }

  public String getPassphrase() {
    return null;
  }

  public boolean promptPassphrase(String message) {
    return false;
  }

  public boolean promptPassword(String message) {
    return false;
  }

  public String[] promptKeyboardInteractive(
      String destination, String name, String instruction, String[] prompt,
      boolean[] echo) {
    return null;
  }

};
