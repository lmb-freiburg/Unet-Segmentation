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

import ij.IJ;
import ij.Prefs;
import ij.WindowManager;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.measure.Calibration;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JDialog;
import javax.swing.GroupLayout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

public class DetectionJob extends SegmentationJob implements PlugIn {

  public DetectionJob() {
    super();
  }

  public DetectionJob(JobTableModel model) {
    super(model);
  }

  @Override
  public void finish() {
    if (progressMonitor().finished()) return;
    readyCancelButton().setEnabled(false);
    try {
      new Thread()
      {
        @Override
        public void run() {
          try {
            Tools.loadSegmentationToImagePlus(
                _localTmpFile, DetectionJob.this,
                _outputScoresCheckBox.isSelected(),
                _outputSoftmaxScoresCheckBox.isSelected(), true);
            if (Recorder.record) {
              Recorder.setCommand(null);
              String command =
                  "call('de.unifreiburg.unet.DetectionJob." +
                  "processHyperStack', " +
                  "'modelFilename=" + model().file.getAbsolutePath() +
                  ",weightsFilename=" + weightsFileName() +
                  "," + model().getTilingParameterString() +
                  ",gpuId=" + selectedGPUString() +
                  ",useRemoteHost=" + String.valueOf(sshSession() != null);
              if (sshSession() != null) {
                command +=
                    ",hostname=" + sshSession().getHost() +
                    ",port=" + String.valueOf(sshSession().getPort()) +
                    ",username=" + sshSession().getUserName();
                if (hostConfiguration().authRSAKey())
                    command += ",RSAKeyfile=" +
                        hostConfiguration().rsaKeyFile();
              }
              command +=
                  ",processFolder=" + processFolder() +
                  ",average=" + (String)_averagingComboBox.getSelectedItem() +
                  ",keepOriginal=" + String.valueOf(
                      _keepOriginalCheckBox.isSelected()) +
                  ",outputScores=" + String.valueOf(
                      _outputScoresCheckBox.isSelected()) +
                  ",outputSoftmaxScores=" + String.valueOf(
                      _outputSoftmaxScoresCheckBox.isSelected()) + "');\n";
              Recorder.recordString(command);
            }
          }
          catch (IOException e) {
            IJ.error("U-Net Detection", e.toString());
          }
          finishJob();
        }
      }.start();
    }
    catch (IllegalThreadStateException e) {}
  }

  public static void processHyperStack(String params)
      throws InterruptedException {
    final DetectionJob job = new DetectionJob();
    if (job._imp == null)
        job.setImagePlus(WindowManager.getCurrentImage());
    if (job._imp == null) {
      IJ.noImage();
      return;
    }

    String[] parameterStrings = params.split(",");
    Map<String,String> parameters = new HashMap<String,String>();
    for (int i = 0; i < parameterStrings.length; i++)
        parameters.put(parameterStrings[i].split("=")[0],
                       parameterStrings[i].split("=")[1]);
    ModelDefinition model = new ModelDefinition();
    model.load(new File(parameters.get("modelFilename")));
    job.setModel(model);
    job.setWeightsFileName(parameters.get("weightsFilename"));
    job.model().setFromTilingParameterString(parameterStrings[2]);
    job.setGPUString(parameters.get("gpuId"));
    if (Boolean.valueOf(parameters.get("useRemoteHost"))) {
      try {
        String hostname = parameters.get("hostname");
        int port = Integer.valueOf(parameters.get("port"));
        String username = parameters.get("username");
        JSch jsch = new JSch();
        jsch.setKnownHosts(
            new File(System.getProperty("user.home") +
                     "/.ssh/known_hosts").getAbsolutePath());
        if (parameters.containsKey("RSAKeyfile"))
            jsch.addIdentity(parameters.get("RSAKeyfile"));
        job.setSshSession(jsch.getSession(username, hostname, port));
        job.sshSession().setUserInfo(new MyUserInfo());

        if (!parameters.containsKey("RSAKeyfile")) {
          final JDialog passwordDialog = new JDialog(
              job._imp.getWindow(), "U-Net Segmentation", true);
          JPanel mainPanel = new JPanel();
          mainPanel.add(new JLabel("Password:"));
          final JPasswordField passwordField = new JPasswordField(15);
          mainPanel.add(passwordField);
          passwordDialog.add(mainPanel, BorderLayout.CENTER);
          JButton okButton = new JButton("OK");
          okButton.addActionListener(
              new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  char[] password = passwordField.getPassword();
                  byte[] passwordAsBytes =
                      HostConfigurationPanel.toBytes(password);
                  job.sshSession().setPassword(passwordAsBytes);
                  Arrays.fill(passwordAsBytes, (byte) 0);
                  Arrays.fill(password, '\u0000');
                  passwordField.setText("");
                  passwordDialog.dispose();
                }});
          passwordDialog.add(okButton, BorderLayout.SOUTH);
          passwordDialog.getRootPane().setDefaultButton(okButton);
          passwordDialog.pack();
          passwordDialog.setMinimumSize(passwordDialog.getPreferredSize());
          passwordDialog.setMaximumSize(passwordDialog.getPreferredSize());
          passwordDialog.setLocationRelativeTo(job._imp.getWindow());
          passwordDialog.setVisible(true);
        }

        job.sshSession().connect();
      }
      catch (JSchException e) {
        IJ.log("Macro call to DetectionJob.processHyperStack aborted. " +
               "Could not establish SSH connection.");
        IJ.error("U-Net Detection", "Could not establish SSH connection.");
        return;
      }
    }
    job.setProcessFolder(parameters.get("processFolder"));
    job._keepOriginalCheckBox.setSelected(
        Boolean.valueOf(parameters.get("keepOriginal")));
    job._outputScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputScores")));
    job._outputSoftmaxScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputSoftmaxScores")));
    job.setInteractive(false);

    job.start();
    job.join();
    job.finish();
  }

};
