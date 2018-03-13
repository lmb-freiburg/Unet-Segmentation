/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

import ij.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;

import java.io.*;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import com.jcraft.jsch.*;

public class HostConfigurationPanel extends JPanel {

  private JCheckBox _useRemoteHostCheckBox =
      new JCheckBox("Use remote host", true);
  private JComboBox<String> _hostComboBox = null;
  private JSpinner _portSpinner = null;
  private JTextField _userTextField = null;
  private final String[] authMethods = { "Password:", "RSA key:" };
  private JComboBox<String> _authMethodComboBox =
      new JComboBox<>(authMethods);
  private JPasswordField _passwordField = null;
  private JTextField _rsaKeyTextField = new JTextField(
      Prefs.get("unet.rsaKeyFilename", ""));
  private Session _sshSession = null;

  private JButton _weightsFileChooseButton = null;
  private JButton _processFolderChooseButton = null;
  private JButton _finetunedFileChooseButton = null;

  public HostConfigurationPanel() {
    _useRemoteHostCheckBox.setToolTipText(
        "Check to use a remote compute server for segmentation");
    final JSeparator useRemoteHostSeparator =
        new JSeparator(SwingConstants.HORIZONTAL);
    final JLabel hostLabel = new JLabel("Host:");
    _hostComboBox = new JComboBox<String>();
    _hostComboBox.setToolTipText(
        "Select / Enter the compute server's host name");
    _hostComboBox.setEditable(true);
    final JLabel portLabel = new JLabel("Port:");
    _portSpinner = new JSpinner(new SpinnerNumberModel(22, 0, 65535, 1));
    _portSpinner.setToolTipText("Remote host's SSH port (Usually 22)");
    JSpinner.NumberEditor _portSpinnerEditor =
        new JSpinner.NumberEditor(_portSpinner,"#");
    _portSpinnerEditor.getTextField().setColumns(5);
    _portSpinner.setEditor(_portSpinnerEditor);
    final JLabel userLabel = new JLabel("Username:");
    _userTextField = new JTextField();
    _userTextField.setToolTipText(
        "Enter your user name for remote SSH login");

    _authMethodComboBox.setMinimumSize(
        _authMethodComboBox.getPreferredSize());
    _authMethodComboBox.setMaximumSize(
        _authMethodComboBox.getPreferredSize());
    final JPanel authParametersPanel = new JPanel(new CardLayout());
    authParametersPanel.setBorder(
        BorderFactory.createEmptyBorder(0, 0, 0, 0));
    _authMethodComboBox.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              ((CardLayout)authParametersPanel.getLayout()).show(
                  authParametersPanel,
                  (String)_authMethodComboBox.getSelectedItem());
            }}});
    _passwordField = new JPasswordField();
    _passwordField.setToolTipText("Enter your SSH password");
    JPanel passwordPanel = new JPanel();
    GroupLayout passwordPanelLayout = new GroupLayout(passwordPanel);
    passwordPanel.setLayout(passwordPanelLayout);
    passwordPanelLayout.setAutoCreateGaps(true);
    passwordPanelLayout.setAutoCreateContainerGaps(false);
    passwordPanelLayout.setHorizontalGroup(
        passwordPanelLayout.createSequentialGroup()
        .addComponent(_passwordField));
    passwordPanelLayout.setVerticalGroup(
        passwordPanelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(_passwordField));
    authParametersPanel.add(passwordPanel, "Password:");

    JPanel rsaKeyPanel = new JPanel();
    GroupLayout rsaPanelLayout = new GroupLayout(rsaKeyPanel);
    rsaKeyPanel.setLayout(rsaPanelLayout);
    _rsaKeyTextField.setToolTipText(
        "Enter the path to your RSA private key");
    final JButton rsaKeyChooseButton;
    if (UIManager.get("FileView.directoryIcon") instanceof Icon)
        rsaKeyChooseButton = new JButton(
            (Icon)UIManager.get("FileView.directoryIcon"));
    else rsaKeyChooseButton = new JButton("...");
    int marginTop = (int) Math.ceil(
        (rsaKeyChooseButton.getPreferredSize().getHeight() -
         _rsaKeyTextField.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (rsaKeyChooseButton.getPreferredSize().getHeight() -
         _rsaKeyTextField.getPreferredSize().getHeight()) / 2.0);
    Insets insets = rsaKeyChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    rsaKeyChooseButton.setMargin(insets);
    rsaKeyChooseButton.setToolTipText(
        "Select your RSA private key file");
    rsaPanelLayout.setAutoCreateGaps(true);
    rsaPanelLayout.setAutoCreateContainerGaps(false);
    rsaPanelLayout.setHorizontalGroup(
        rsaPanelLayout.createSequentialGroup()
        .addComponent(_rsaKeyTextField).addComponent(rsaKeyChooseButton));
    rsaPanelLayout.setVerticalGroup(
        rsaPanelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(_rsaKeyTextField).addComponent(rsaKeyChooseButton));
    authParametersPanel.add(rsaKeyPanel, "RSA key:");

    GroupLayout layout = new GroupLayout(this);
    setLayout(layout);
    layout.setAutoCreateGaps(true);
    layout.setAutoCreateContainerGaps(false);
    layout.setHorizontalGroup(
        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addGroup(
            layout.createSequentialGroup()
            .addComponent(_useRemoteHostCheckBox)
            .addComponent(useRemoteHostSeparator))
        .addGroup(
            layout.createSequentialGroup()
            .addComponent(hostLabel)
            .addComponent(
                _hostComboBox, GroupLayout.PREFERRED_SIZE,
                GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(portLabel)
            .addComponent(
                _portSpinner, GroupLayout.PREFERRED_SIZE,
                GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
        .addGroup(
            layout.createSequentialGroup()
            .addComponent(userLabel)
            .addComponent(
                _userTextField, 0, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE)
            .addComponent(_authMethodComboBox)
            .addComponent(
                authParametersPanel, GroupLayout.PREFERRED_SIZE,
                GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE)));
    layout.setVerticalGroup(
        layout.createSequentialGroup()
        .addGroup(
            layout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(
                _useRemoteHostCheckBox, GroupLayout.PREFERRED_SIZE,
                GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(
                useRemoteHostSeparator, GroupLayout.PREFERRED_SIZE,
                GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
        .addGroup(
            layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(hostLabel)
            .addComponent(_hostComboBox)
            .addComponent(portLabel)
            .addComponent(_portSpinner))
        .addGroup(
            layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(userLabel)
            .addComponent(_userTextField)
            .addComponent(_authMethodComboBox)
            .addComponent(authParametersPanel)));

    _useRemoteHostCheckBox.addChangeListener(new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            JCheckBox c = (JCheckBox) e.getSource();
            hostLabel.setEnabled(c.isSelected());
            _hostComboBox.setEnabled(c.isSelected());
            portLabel.setEnabled(c.isSelected());
            _portSpinner.setEnabled(c.isSelected());
            userLabel.setEnabled(c.isSelected());
            _userTextField.setEnabled(c.isSelected());
            _authMethodComboBox.setEnabled(c.isSelected());
            authParametersPanel.setEnabled(c.isSelected());
          }});

    rsaKeyChooseButton.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFile = new File(_rsaKeyTextField.getText());
            JFileChooser f = new JFileChooser(startFile);
            f.setDialogTitle("Select RSA private key file");
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int res = f.showDialog(null, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _rsaKeyTextField.setText(
                f.getSelectedFile().getAbsolutePath());
          }});

    // Load preferences
    _authMethodComboBox.setSelectedItem(
        Prefs.get("unet.authMethod", "Password:"));

    int nHosts = (int)Prefs.get("unet.hosts_size", 0);
    if (nHosts != 0) {
      for (int i = 0; i < nHosts; ++i) {
        String host = Prefs.get("unet.hosts_" + i, "");
        if (!host.equals("")) _hostComboBox.addItem(host);
      }
    }
    String hostname = Prefs.get("unet.hostname", "");
    if (hostname != "") _hostComboBox.setSelectedItem(hostname);
    _portSpinner.setValue((int)Prefs.get("unet.port", 22));
    _userTextField.setText(
        Prefs.get("unet.username", System.getProperty("user.name")));

    // The initial CheckBox status is true, if the stored setting indicate
    // false, update and disable server controls
    _useRemoteHostCheckBox.setSelected(
        (boolean)Prefs.get("unet.useRemoteHost", false));
  }

  boolean useRemoteHost() {
    return _useRemoteHostCheckBox.isSelected();
  }

  String hostname() {
    return (String)_hostComboBox.getSelectedItem();
  }

  int port() {
    return (Integer) _portSpinner.getValue();
  }

  String username() {
    return _userTextField.getText();
  }

  String authMethod() {
    return (String)_authMethodComboBox.getSelectedItem();
  }

  boolean authPassword() {
    return _authMethodComboBox.getSelectedItem().equals("Password:");
  }

  boolean authRSAKey() {
    return _authMethodComboBox.getSelectedItem().equals("RSA key:");
  }

  String rsaKeyFile() {
    return _rsaKeyTextField.getText();
  }

  JButton weightsFileChooseButton() {
    if (_weightsFileChooseButton == null) {
      if (UIManager.get("FileView.directoryIcon") instanceof Icon)
          _weightsFileChooseButton = new JButton(
              (Icon)UIManager.get("FileView.directoryIcon"));
      else _weightsFileChooseButton = new JButton("...");
      _weightsFileChooseButton.setVisible(!useRemoteHost());
      _useRemoteHostCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JCheckBox c = (JCheckBox) e.getSource();
              _weightsFileChooseButton.setVisible(!c.isSelected());
            }});
    }
    return _weightsFileChooseButton;
  }

  JButton processFolderChooseButton() {
    if (_processFolderChooseButton == null) {
      if (UIManager.get("FileView.directoryIcon") instanceof Icon)
          _processFolderChooseButton = new JButton(
              (Icon)UIManager.get("FileView.directoryIcon"));
      else _processFolderChooseButton = new JButton("...");
      _processFolderChooseButton.setVisible(!useRemoteHost());
      _useRemoteHostCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JCheckBox c = (JCheckBox) e.getSource();
              _processFolderChooseButton.setVisible(!c.isSelected());
            }});
    }
    return _processFolderChooseButton;
  }

  JButton finetunedFileChooseButton() {
    if (_finetunedFileChooseButton == null) {
      if (UIManager.get("FileView.directoryIcon") instanceof Icon)
          _finetunedFileChooseButton = new JButton(
              (Icon)UIManager.get("FileView.directoryIcon"));
      else _finetunedFileChooseButton = new JButton("...");
      _finetunedFileChooseButton.setVisible(!useRemoteHost());
      _useRemoteHostCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              JCheckBox c = (JCheckBox) e.getSource();
              _finetunedFileChooseButton.setVisible(!c.isSelected());
            }});
    }
    return _finetunedFileChooseButton;
  }

  Session sshSession() throws JSchException {

    if (_sshSession != null) {

      // Return existing SSH Session if host, port and user match
      if (useRemoteHost() && _sshSession.isConnected() &&
          _sshSession.getHost() == hostname() &&
          _sshSession.getPort() == port() &&
          _sshSession.getUserName() == username()) return _sshSession;

      // Otherwise disconnect
      if (_sshSession.isConnected()) {
        IJ.log("Disconnecting from " + _sshSession.getHost());
        _sshSession.disconnect();
      }

      _sshSession = null;
    }

    if (useRemoteHost()) {

      // Open new SSH Session
      IJ.log("Connecting to '" + hostname() + "'");
      JSch jsch = new JSch();
      jsch.setKnownHosts(new File(System.getProperty("user.home") +
                                  "/.ssh/known_hosts").getAbsolutePath());
      if (authRSAKey()) jsch.addIdentity(rsaKeyFile());
      _sshSession = jsch.getSession(username(), hostname(), port());
      _sshSession.setUserInfo(new MyUserInfo());
      if (authPassword()) {
        char[] password = _passwordField.getPassword();
        byte[] passwordAsBytes = toBytes(password);
        _sshSession.setPassword(passwordAsBytes);

        // Overwrite password and clear password field
        Arrays.fill(passwordAsBytes, (byte) 0);
        Arrays.fill(password, '\u0000');
        _passwordField.setText("");
      }
      _sshSession.connect();

      // Login was successful => save host to preferences
      Prefs.set("unet.useRemoteHost", true);
      int nHosts = _hostComboBox.getItemCount();
      boolean found = false;
      for (int i = 0; i < nHosts; ++i) {
        Prefs.set("unet.hosts_" + i, _hostComboBox.getItemAt(i));
        found |= _sshSession.getHost().equals(_hostComboBox.getItemAt(i));
      }
      if (!found) {
        Prefs.set("unet.hosts_" + nHosts, hostname());
        nHosts++;
      }
      Prefs.set("unet.hosts_size", nHosts);
      Prefs.set("unet.hostname", hostname());
      Prefs.set("unet.port", port());
      Prefs.set("unet.username", username());
      Prefs.set("unet.authMethod", authMethod());
      if (authRSAKey())
          Prefs.set("unet.rsaKeyFilename", rsaKeyFile());
    }
    else Prefs.set("unet.useRemoteHost", false);

    return _sshSession;
  }

  public static byte[] toBytes(char[] chars)
        {
          CharBuffer charBuffer = CharBuffer.wrap(chars);
          ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(charBuffer);
          byte[] bytes = Arrays.copyOfRange(
              byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
          Arrays.fill(charBuffer.array(), '\u0000');
          Arrays.fill(byteBuffer.array(), (byte) 0);
          return bytes;
        }
}
